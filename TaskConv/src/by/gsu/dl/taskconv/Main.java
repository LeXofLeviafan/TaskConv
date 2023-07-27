package by.gsu.dl.taskconv;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Converts tasks with group tests from known formats in DL test format.
 * @author Alexey Gulenko
 * @version 1.2.2
 */
public class Main implements Callable<String> {

    /** Configuration data. */
    private final Config config;
    /** Detected task type. */
    private TaskType type = null;

    /**
     * Private application constructor.
     * @param theConfig    configuration
     */
    private Main(final Config theConfig) {
        config = theConfig;
    }

    /**
     * Receives configuration data.
     * @param args    commandline arguments
     * @return application class instance
     */
    private static Main init(final String[] args) throws RageExitException {
        return new Main( new Config(args) );
    }

    /**
     * Receives configuration data.
     * @param workDir application base dir (with config files)
     * @param args    commandline arguments
     * @return application class instance
     */
    public static Main init(final File workDir, final String... args) throws RageExitException {
        return new Main( new Config(args, workDir) );
    }

    /**
     * Determines task type.
     * @return task type name
     */
    private String body () throws RageExitException {
        config.verboseMessage("");
        String[][] files = getFileList();
        List<TaskType> taskTypes = new ArrayList<TaskType>(files.length);
        for (File typeFile : config.getTaskTypes()) {
            TaskType type = TaskType.process(typeFile, config, files);
            if (type == null) {
                continue;
            }
            taskTypes.add(type);
        }
        config.verboseMessage("");
        if (taskTypes.size() == 0) {
            config.rageExit(Config.Errors.DETECT_FAILED);
        }
        if (taskTypes.size() == 1) {
            type = taskTypes.get(0);
            config.regularMessage(Config.DETECTED + type.getName());
            return type.getName();
        }
        Collections.sort(taskTypes);
        Collections.reverse(taskTypes);
        if (config.isAutoOn()) {
            type = taskTypes.get(0);
            config.regularMessage(taskTypes.size() + Config.ASSUMING + type.getName());
            return type.getName();
        }
        type = choice(taskTypes);
        if (type == null) {
            config.regularMessage("Received exit command");
            return null;
        }
        return type.getName();
    }

    /**
     * Generates file list to parse.
     * @return list of File objects
     */
    private String[][] getFileList() {
        String taskDir = config.getTaskDir();
        String[] tasks = config.getTasks();
        String[][] result = new String[tasks.length][];
        for (int task = 0; task < result.length; task++) {
            List<File> files = new ArrayList<File>(FileUtils.listFiles(new File(taskDir + tasks[task]), null, true));
            result[task] = new String[files.size()];
            for (int i = 0; i < result[task].length; i++) {
                result[task][i] = Config.normalizePath( files.get(i).toString() );
            }
        }
        return result;
    }

    /**
     * Asks user to select one of the given variants.
     * @param items    list of variants
     * @param <T>      class of items to choose from
     * @return selected item if any
     */
    private <T> T choice (List<T> items) throws RageExitException {
        System.out.flush();
        System.out.flush();
        System.out.println("More than one type was detected. Asking for input.");
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println();
            System.out.println("Choose one of the following:");
            System.out.println("[ 0] cancel");
            for (int i = 0; i < items.size(); i++) {
                System.out.format("[%2d] %s", i+1, items.get(i).toString());
                System.out.println();
            }
            System.out.print("> ");
            Integer num = null;
            try {
                num = in.nextInt();
            } catch (InputMismatchException e) {
                System.out.println("Input is invalid!");
                continue;
            } catch (Exception e) {
                config.rageExit("Can't read from input!");
            }
            if (num == 0) {
                in.close();
                return null;
            }
            try {
                T result = items.get(num - 1);
                in.close();
                return result;
            } catch (IndexOutOfBoundsException e) {
                System.out.println("Incorrect choice!");
                continue;
            }
        }
    }

    /**
     * Does moving/copying for a single file.
     * @param oldName    old file name
     * @param newName    new file name
     */
    private void doFile (final String fro, final String oldName, final String to, final String newName)
            throws RageExitException {
        final Path oldPath = Paths.get(fro + oldName);
        final Path newPath = Paths.get(to + newName);
        try {
            if (config.isMoveOn()) {
                Files.move(oldPath, newPath);
            } else {
                Files.copy(oldPath, newPath);
            }
            config.verboseMessage("\"" + oldName + "\" " + config.getArrow() + " \"" + newName + "\"");
        } catch (IOException e) {
            config.rageExit("Failed to copy/move files");
        }
    }

    /**
     * Tests if directory is empty
     * @param directory    directory path
     * @return true if empty
     */
    public static boolean isDirEmpty (final Path directory) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            return !stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Recursively remove empty directories.
     * @param currentDir    directory to remove
     */
    private void clean(final String currentDir) {
        File file = new File(currentDir);
        String[] directories = Config.getSubDirs(file);
        if (directories != null) {
            for (String dir : directories) {
                clean(currentDir + '/' + dir);
            }
        }
        try {
            if (isDirEmpty(file.toPath()) && Files.deleteIfExists(file.toPath())) {
                config.verboseMessage("Removed empty directory: \"" + file + "\".");
            }
        } catch (DirectoryNotEmptyException e) {
            // skipping
        } catch (IOException e) {
            config.regularMessage("Couldn't remove directory: \"" + file + "\".");
        }
    }

    /**
     * Does moving/copying and calculates marks.
     * @return marks list
     */
    private String[][] doFiles () throws RageExitException {
        final String taskDir = config.getTaskDir();
        final String workDir = config.getWorkDir();
        final String[] tasks = config.getTasks();
        FilePattern.FileProcessed[][]
                inFiles = type.getInFiles(),
                outFiles = type.getOutFiles();
        String[][] marks = new String[inFiles.length][];
        for (int task = 0; task < tasks.length; task++) {
            marks[task] = new String[inFiles[task].length];
            final String dir = workDir + tasks[task];
            if (!Files.exists(Paths.get(dir))) {
                new File(dir).mkdirs();
                config.verboseMessage("Creating task directory \"" + dir + "\".");
            }
            for (int i = 0; i < inFiles[task].length; i++) {
                doFile(taskDir + tasks[task], inFiles[task][i].path, dir, (i+1) + ".in");
                if (!config.checkInfilesOnly()) {
                    doFile(taskDir + tasks[task], outFiles[task][i].path, dir, (i+1) + ".out");
                }
                marks[task][i] = "-1";
                if (i > 0 && inFiles[task][i].groupNumber != inFiles[task][i-1].groupNumber) {
                    marks[task][i-1] = "1";
                }
            }
            marks[task][marks[task].length-1] = "1";
        }
        if (config.isCleanOn()) {
            config.verboseMessage("");
            clean(config.getTaskDir());
        }
        return marks;
    }

    /**
     * Processing files and printing out results.
     */
    private void pout () throws RageExitException {
        config.verboseMessage("");
        final String[][] marks = doFiles();
        config.verboseMessage("");
        final String[] tasks = config.getTasks();
        final String workDir = config.getWorkDir();
        final String outFile = config.getOutFile();
        boolean failed = false;
        for (int task = 0; task < marks.length; task++) {
            final Path taskOutFile = Paths.get(workDir + tasks[task] + outFile);
            config.verboseMessage("Writing to \"" + taskOutFile + "\"...");
            try {
                Files.write(taskOutFile, Arrays.asList(marks[task]), Charset.defaultCharset());
            } catch (IOException e) {
                config.verboseMessage(" can't write to OutFile!");
                failed = true;
                continue;
            }
            config.verboseMessage(" done.");
        }
        if (failed) {
            config.rageExit("Couldn't write to some OutFiles!");
        }
        config.regularMessage("Conversion finished successfully.");
    }

    /**
     * Functionality implementation.
     * @return determined task type name (null if cancelled manually)
     */
    public String call() throws RageExitException {
        final String result = body();
        if (result != null)
            pout();
        return result;
    }

    /**
     * Runner method.
     * @param args    commandline arguments
     */
    public static void main (final String[] args) {
        try {
            Main app = init(args);
            app.call();
        } catch (RageExitException e) {
            System.exit(1);
        }
    }
}
