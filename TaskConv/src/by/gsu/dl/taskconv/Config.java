package by.gsu.dl.taskconv;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Configuration data.
 * @author Alexey Gulenko
 * @version 1.2.1
 */
public class Config {

    /** -v: Type assuming line. */
    public static final String ASSUMING = " types detected, assuming ";
    /** -v: Type detecting line. */
    public static final String DETECTED = "Detected task type ";
    /** -v: Arrow for copying files. */
    private static final String COPY_ARROW = "->";
    /** -v: Arrow for moving files. */
    private static final String MOVE_ARROW = "=>";

    /** RageExit lines. */
    public enum Errors {
        TOO_MANY("You cannot specify a path option twice!"),
        NO_DIR("No taskDir specified!"),
        NO_TASKS("No subdirectories found at specified level!"),
        BAD_DIR("No such directory exists!"),
        BAD_TYPE("Task type not recognized!"),
        OUT_FILE_EXISTS("I refuse to rewrite existing outFile!"),
        WORKDIR_FILE_EXISTS("I refuse to replace existing file with a directory!"),
        DETECT_FAILED("Failed to detect task type!");

        private final String message;
        private Errors (final String msg) {
            message = msg;
        }
        @Override
        public String toString() {
            return message;
        }
    };

    /** Configuration file lines info. */
    private enum CfgLines {
        ABC(0);
        private final int line;
        private CfgLines(int lineNumber) {
            line = lineNumber;
        }
        public int number() {
            return line;
        }
    }

    /** Types DB dir. */
    private static final String DATA_DIR = "./DB/";
    /** File with changeable values. */
    private static final String CFG = ".cfg";

    /** Letters (${SL}). */
    public static final String LETTERS = "a-z";
    /** Directory separator. */
    private static final String SL = "/";

    /** Default CFG contents. */
    private static final String[] DEFAULT_CFG = {LETTERS};

    /** Chars(${taskName}). */
    private final String abc;
    /** Getter method for {@link #abc} */
    private String getAbc() { return abc; }
    /** -v: arrow for moving/copying files. */
    private String arrow = COPY_ARROW;
    /** Getter method for {@link #arrow} */
    public String getArrow() { return arrow; }

    /** Task name template. */
    private String taskName = null;
    /** Getter method for {@link #taskName} */
    public String getTaskName() { return taskName; }
    /** Output file name. */
    private String outFile = "marks.txt";
    /** Getter method for {@link #outFile} */
    public String getOutFile() { return outFile; }
    /** Task type name prefix. */
    private String taskType = "";
    /** Task directory name. */
    private String taskDir = null;
    /** Getter method for {@link #taskDir} */
    public String getTaskDir() { return taskDir; }
    /** Task subdirectories depth. */
    private int taskDepth = 0;
    /** Task list. */
    private String[] tasks = null;
    /** Getter method for {@link #tasks} */
    public String[] getTasks() { return tasks; }
    /** Output directory name. */
    private String workDir = null;
    /** Getter method for {@link #workDir} */
    public String getWorkDir() { return workDir; }

    /** Quiet output. */
    private boolean auto = false;
    /** Getter method for {@link #infilesOnly} */
    public boolean isAutoOn() { return auto; }

    /** Verbosity levels. */
    public enum Verbosity{ QUIET, NORMAL, VERBOSE; }
    /** Console output verbosity. */
    private Verbosity verbosity = Verbosity.NORMAL;
    /** Move instead of copying. */
    private boolean move = false;
    /** Getter method for {@link #move} */
    public boolean isMoveOn() { return move; }
    /** Clean after moving. */
    private boolean clean = true;
    /** Getter method for {@link #clean} */
    public boolean isCleanOn() { return clean; }

    /** Quiet output. */
    private boolean infilesOnly = false;
    /** Getter method for {@link #infilesOnly} */
    public boolean checkInfilesOnly() { return infilesOnly; }

    /**
     * Processes basic config file.
     * @return list of config file lines
     */
    private List<String> getCfg() {
        try {
            List <String> lines = Files.readAllLines(Paths.get(CFG), Charset.defaultCharset());
            if (lines.isEmpty()) {
                lines.add("");
            }
            if (lines.get(0).trim().isEmpty()) {
                lines.set(0, LETTERS);
            } else {
                lines.set(0, Pattern.quote(lines.get(0).toLowerCase(Locale.getDefault())));
            }
            return lines;
        } catch (IOException e) {
            verboseMessage("File \"" + CFG + "\" not found, falling back to defaults.");
            return Arrays.asList(DEFAULT_CFG);
        }
    }

    /**
     * Reads configuration from environment.
     * @param args    application argument list
     */
    public Config(final String[] args) {
        processArgs(args);
        List<String> cfg = getCfg();
        abc = '[' + cfg.get(CfgLines.ABC.number()) + ']';
        prepareVals();
    }

    /**
     * Process commandline arguments.
     * @param args    commandline arguments
     */
    private void processArgs(final String[] args) {
        CommandLine arguments = Arguments.parse(args);
        for (Arguments option : Arguments.values()) {
            if (!arguments.hasOption(option.toChar())) {
                continue;
            }
            String[] values = arguments.getOptionValues(option.toChar());
            if (values == null) {
                setBoolean(option);
            } else if (values.length == 1) {
                setString(option, normalizePath(values[0]));
            } else {
                rageExit(Errors.TOO_MANY+" { --" + option + "=\"" + values[1] + "\" }");
            }
        }
        String[] values = arguments.getArgs();
        if (values != null) {
            for (String value : values) {
                setTaskOrWorkDir(normalizePath(value));
            }
        }
    }

    /**
     * Prepares values for execution.
     */
    private void prepareVals() {
        // taskDir
        if (taskDir == null || "".equals(taskDir)) {
            rageExit(Errors.NO_DIR, true);
        }
        if (!Files.isDirectory(Paths.get(taskDir))) {
            rageExit(Errors.BAD_DIR, true);
        }
        if (!taskDir.endsWith(SL)) {
            taskDir += SL;
        }
        regularMessage("Processing directory \"" + taskDir + "\".");

        // tasks
        tasks = generateDirs(taskDir, "", taskDepth).toArray(new String[0]);
        if (tasks.length == 0) {
            rageExit(Errors.NO_TASKS);
        }

        // outFile
        if (Files.exists(Paths.get(taskDir + outFile))) {
            rageExit(Errors.OUT_FILE_EXISTS + " (" + taskDir + outFile + ")");
        }

        // taskType
        taskType += '*';
        if (getTaskTypes().length == 0) {
            rageExit(Errors.BAD_TYPE);
        }

        // taskName
        if (taskName == null) {
            taskName = getAbc() + '+';
        }

        // workDir
        if (workDir == null) {
            workDir = taskDir;
        } else {
            if (!workDir.endsWith(SL)) {
                workDir += SL;
            }
        }
        Path dir = Paths.get(workDir);
        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            rageExit(Errors.WORKDIR_FILE_EXISTS);
        }

        // Move, Clean
        if (move) {
            arrow = MOVE_ARROW;
        } else {
            if (!clean) {
                regularMessage("--move never used, ignoring --keep.");
            }
            clean = false;
        }

    }

    /**
     * Processes boolean options.
     * @param option    option commandline argument
     */
    private void setBoolean(final Arguments option) {
        switch (option) {
            case AUTO:
                auto = true;
                break;
            case QUIET:
            case VERBOSE:
                setVerbosity(option);
                break;
            case MOVE:
                move = true;
                break;
            case KEEP:
                clean = false;
                break;
            case INFILES_ONLY:
                infilesOnly = true;
                break;
        }
    }

    /**
     * Processes string options.
     * @param option    option commandline argument
     * @param value     option value
     */
    private void setString(final Arguments option, final String value) {
        switch (option) {
            case TASK_TYPE:
                taskType = value;
                break;
            case OUTPUT_FILE:
                outFile = value;
                break;
            case DIRECTORY:
                setTaskDir(value);
                break;
            case LEVEL:
                try {
                    taskDepth = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    rageExit("Level must be a number!");
                }
                if (taskDepth < 0) {
                    rageExit("Level must be a non-negative number!");
                }
                break;
            case TASK_NAME:
                setTaskName(value);
                break;
            case WORK_DIR:
                workDir = value;
        }
    }

    /**
     * Sets output verbosity.
     * @param option    option commandline argument
     */
    private void setVerbosity(final Arguments option) {
        if (option.equals(Arguments.QUIET)) {
            verbosity = Verbosity.QUIET;
            auto = true;
        } else {
            verbosity = Verbosity.VERBOSE;
        }
    }

    /**
     * Prints out message if verbosity isn't set to quiet.
     * @param message    message to print
     */
    public void regularMessage(final String message) {
        if (!verbosity.equals(Verbosity.QUIET)) {
            System.out.println(message);
        }
    }

    /** Checks if verbosity is set to verbose. */
    public void verboseMessage(final String message) {
        if (verbosity.equals(Verbosity.VERBOSE)) {
            System.out.println(message);
        }
    }

    /**
     * Generates list of files containing task types.
     * @return array of File objects
     */
    public File[] getTaskTypes() {
        FileFilter filter = new WildcardFileFilter(taskType, IOCase.INSENSITIVE);
        return new File(DATA_DIR).listFiles(filter);
    }

    /**
     * Replacess all occurrences of {@link File#separator} with {@link #SL}.
     * @param path    path to be normalized
     * @return normalized path
     */
    public static String normalizePath(final String path) {
        if (!SL.equals(File.separator)) {
            return path.replaceAll(Pattern.quote(File.separator), SL);
        }
        return path;
    }

    /**
     * Sets task directory.
     * @param value option value
     */
    private void setTaskDir(final String value) {
        if (taskDir != null) {
            rageExit(Errors.TOO_MANY+" { --" + Arguments.DIRECTORY + "=\"" + value + "\" }");
        }
        taskDir = value;
    }

    /**
     * Sets task or work directory.
     * @param value option value
     */
    private void setTaskOrWorkDir(final String value) {
        if (taskDir == null) {
            taskDir = value;
            return;
        }
        if (workDir == null) {
            workDir = value;
            return;
        }
        rageExit(Errors.TOO_MANY+" { \"" + value + "\" }");
    }

    /**
     * Generates list of subdirectories on the given level.
     * @param root      root directory to search in
     * @param parent    current subdirectory
     * @param depth     current level
     * @return list of subdirectories
     */
    private List<String> generateDirs(final String root, final String parent, final int depth) {
        final List<String> result = new ArrayList<String>();
        if (depth == 0) {
            result.add(parent);
            return result;
        }
        final String[] dirs = getSubDirs(new File(root + parent));
        for (String dir : dirs) {
            for (String subdir : generateDirs(root, parent + dir + SL, depth-1)) {
                result.add(subdir);
            }
        }
        return result;
    }

    /**
     * Generates list of subdirectories for the given one.
     * @param parent    directory to scan
     * @return array of directory names
     */
    public static String[] getSubDirs(final File parent) {
        return parent.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isDirectory();
            }
        });
    }

    /**
     * Sets task name.
     * @param value option value.
     */
    private void setTaskName (final String value) {
        taskName = Pattern.quote(value);
    }

    /**
     * Stops execution with an error message.
     * @param error Error message.
     */
    public void rageExit(final String error) {
        regularMessage(error);
        System.exit(1);
    }
    /**
     * Stops execution with an error message.
     * @param error    error message
     * @param usage    true if usage info needed
     */
    private void rageExit(final Errors error, boolean usage) {
        regularMessage(error+"");
        if (usage && !verbosity.equals(Verbosity.QUIET)) {
            Arguments.printUsage();
        }
        System.exit(1);
    }
    /**
     * Stops execution with an error message.
     * @param error    error message
     */
    public void rageExit(final Errors error) {
        rageExit(error, false);
    }
}
