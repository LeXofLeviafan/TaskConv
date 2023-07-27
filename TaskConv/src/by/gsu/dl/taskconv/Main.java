package by.gsu.dl.taskconv;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Converts tasks with group tests from known formats in DL test format.
 * @author Alexey Gulenko
 * @version 1.2
 */
public class Main {

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
    private static Main init(final String[] args) {
        return new Main(new Config(args));
    }

    /**
     * Determines task type.
     */
    private void body() {
        List<File> files = getFileList();
        //System.out.println(files);
        List<TaskType> taskTypes = new ArrayList<TaskType>(files.size());
        for (File typeFile : config.getTaskTypes()) {
            TaskType type = TaskType.process(typeFile, config, files);
            if (type == null) {
                continue;
            }
            taskTypes.add(type);
        }
        if (taskTypes.size() == 0) {
            config.rageExit(Config.Errors.DETECT_FAILED);
        }
        if (taskTypes.size() == 1) {
            type = taskTypes.get(0);
            if (!config.isQuietOn()) {
                System.out.println(Config.DETECTED + type.getName());
            }
            return;
        }
        Collections.sort(taskTypes);
        Collections.reverse(taskTypes);
        if (config.isAutoOn()) {
            type = taskTypes.get(0);
            System.out.println(taskTypes.size() + Config.ASSUMING + type.getName());
            return;
        }
        type = choice(taskTypes);
        if (type == null) {
            System.out.println("Received exit command");
            System.exit(0);
        }
    }

    /**
     * Generates file list to parse.
     * @return list of File objects
     */
    private List<File> getFileList() {
        File taskDir = new File(config.getTaskDir());
        List<File> files = new ArrayList<File>(FileUtils.listFiles(taskDir, null, true));
        if (File.separator.equals(Config.SL)) {
            return files;
        }
        for (ListIterator<File> current = files.listIterator(); current.hasNext();) {
            File file = current.next();
            String newName = file.toString().replaceAll(File.separator, Config.SL);
            current.set(new File(newName));
        }
        return files;
    }

    /**
     * Asks user to select one of the given variants.
     * @param items    list of variants
     * @param <T>      class of items to choose from
     * @return selected item if any
     */
    private <T> T choice(List<T> items) {
        System.err.flush();
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
                return null;
            }
            try {
                return items.get(num-1);
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
    private void doFile (final String fro, final String oldName, final String to, final String newName) {
        final Path oldPath = Paths.get(fro + oldName);
        final Path newPath = Paths.get(to + newName);
        try {
            if (config.isMoveOn()) {
                Files.move(oldPath, newPath);
            } else {
                Files.copy(oldPath, newPath);
            }
            if (config.isVerboseOn()) {
                System.out.println("\"" + oldName + "\" " + config.getArrow() + " \"" + newName + "\"");
            }
        } catch (IOException e) {
            config.rageExit("Failed to copy/move files");
        }
    }

    /**
     * Recursively remove empty directories.
     * @param currentDir    directory to remove
     * @return true if directory was removed, false otherwise.
     */
    private void clean(final String currentDir) {
        File file = new File(currentDir);
        String[] directories = file.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isDirectory();
            }
        });
        if (directories != null) {
            for (String dir : directories) {
                clean(currentDir + '/' + dir);
            }
        }
        try {
            if (Files.deleteIfExists(file.toPath())) {
                if (config.isVerboseOn()) {
                    System.out.println("Removed empty directory: \"" + file + "\".");
                }
            }
        } catch (DirectoryNotEmptyException e) {
            // skipping
        } catch (IOException e) {
            System.err.println("Couldn't remove directory: \"" + file + "\".");
        }
    }

    /**
     * Does moving/copying and calculates marks.
     * @return marks list
     */
    private String[] doFiles() {
        final String taskDir = config.getTaskDir();
        final String workDir = config.getWorkDir();
        FilePattern.FileProcessed[]
                infiles = type.getInfiles(),
                outfiles = type.getOutfiles();
        String[] marks = new String[infiles.length];
        for (int i = 0; i < infiles.length; i++) {
            doFile(taskDir, infiles[i].path, workDir,(i+1) + ".in");
            if (!config.checkInfilesOnly()) {
                doFile(taskDir, outfiles[i].path, workDir, (i+1) + ".out");
            }
            marks[i] = "-1";
            if (i > 0 && infiles[i].groupNumber != infiles[i-1].groupNumber) {
                marks[i-1] = "1";
            }
        }
        marks[marks.length-1] = "1";
        if (config.isCleanOn()) {
            clean(config.getTaskDir());
        }
        return marks;
    }

    /**
     * Processing files and printing out results.
     */
    private void pout() {
        String[] marks = doFiles();
        if (config.isVerboseOn()) {
            System.out.print("Writing to \"" + config.getOutFile() + "\"...");
        }
        try {
            Files.write(Paths.get(config.getWorkDir() + config.getOutFile()),
                    Arrays.asList(marks), Charset.defaultCharset());
        } catch (IOException e) {
            config.rageExit("Can't write to OutFile!");
        }
        if (config.isVerboseOn()) {
            System.out.println(" done.");
        }
    }

    /**
     * Runner method.
     * @param args    commandline arguments
     */
    public static void main(final String[] args) {
        Main app = init(args);
        app.body();
        app.pout();
    }
}
