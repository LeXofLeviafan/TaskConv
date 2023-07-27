package by.gsu.dl.taskconv;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Configuration data.
 * @author Alexey Gulenko
 * @version 1.2
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
        TOO_MANY("You cannot specify an option twice!"),
        NO_DIR("No taskDir specified!"),
        BAD_DIR("No such directory exists!"),
        BAD_TYPE("Task type not recognized!"),
        CANT_CLEAN("Option -c can only be used together with -m!"),
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
    public static final String SL = "/";

    /** Default CFG contents. */
    private static final String[] DEFAULT_CFG = {LETTERS};

    /** Chars(${taskName}). */
    private final String abc;
    /** Getter method for {@link #abc} */
    public String getAbc() { return abc; }
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
    /** Checks if verbosity is set to quiet. */
    public boolean isQuietOn() { return verbosity.equals(Verbosity.QUIET); }
    /** Checks if verbosity is set to verbose. */
    public boolean isVerboseOn() { return verbosity.equals(Verbosity.VERBOSE); }

    /** Move instead of copying. */
    private boolean move = false;
    /** Getter method for {@link #move} */
    public boolean isMoveOn() { return move; }
    /** Clean after moving. */
    private boolean clean = false;
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
    private static List<String> getCfg() {
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
            System.err.println("File \"" + CFG + "\" not found!");
            return Arrays.asList(DEFAULT_CFG);
        }
    }

    /**
     * Reads configuration from environment.
     * @param args    application argument list
     */
    public Config(final String[] args) {
        List<String> cfg = getCfg();
        abc = '[' + cfg.get(CfgLines.ABC.number()) + ']';
        processArgs(args);
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
                setString(option, values[0]);
            } else {
                rageExit(Errors.TOO_MANY+" { --" + option + "=\"" + values[1] + "\" }");
            }
        }
        String[] values = arguments.getArgs();
        if (values != null) {
            for (String value : values) {
                setTaskOrWorkDir(value);
            }
        }
    }

    /**
     * Prepares values for execution.
     */
    private void prepareVals() {
        // taskDir
        if (taskDir == null || "".equals(taskDir)) {
            rageExit(Errors.NO_DIR);
        }
        if (!Files.isDirectory(Paths.get(taskDir))) {
            rageExit(Errors.BAD_DIR);
        }
        if (!taskDir.endsWith(SL)) {
            taskDir += SL;
        }
        if (!isQuietOn()) {
            System.out.println("Processing directory \"" + taskDir + "\".");
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
        if (!Files.exists(dir)) {
            new File(workDir).mkdirs();
            if (isVerboseOn()) {
                System.out.println("Creating work directory \"" + workDir + "\"");
            }
        }

        // Move, Clean
        if (move) {
            arrow = MOVE_ARROW;
        } else if (clean) {
            rageExit(Errors.CANT_CLEAN);
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
            case CLEAN:
                clean = true;
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
        } else {
            verbosity = Verbosity.VERBOSE;
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
        rageExit(Errors.TOO_MANY+" { --" + Arguments.DIRECTORY + "=\"" + value + "\" }");
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
        if (!isQuietOn()) {
            System.err.println(error);
        }
        System.exit(1);
    }
    public void rageExit(final Errors error) {
        rageExit(error+"");
    }
}
