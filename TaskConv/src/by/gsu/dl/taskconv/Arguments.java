package by.gsu.dl.taskconv;

import org.apache.commons.cli.*;

/**
 * Commandline options.
 * @author Alexey Gulenko
 * @version 1.2.1
 */
public enum Arguments {
    AUTO('a', "auto", null, "decide on task type automatically if result is ambiguous"),
    HELP('h', "help", null, "print this message"),
    QUIET('q', "quiet", null, "suppress all messages (also applies --auto)"),
    VERBOSE('v', "verbose", null, "verbose output"),
    MOVE('m', "move", null, "move files instead of copying"),
    KEEP('k', "keep", null, "when used with --move, don't remove empty "
            + "directories recursively from task directory after processing files"),
    INFILES_ONLY('i', "infiles-only", null, "process input files only (for tasks without output files)"),
    TASK_TYPE('t', "type", "prefix", "required prefix of the task type (IOI, CEOI etc)"),
    OUTPUT_FILE('o', "output", "filename", "output filename (result); default is \"marks.txt\""),
    DIRECTORY('d', "directory", "path", "task directory (required)"),
    LEVEL('l', "level", "int", "depth level (default is 0)"),
    WORK_DIR('w', "workdir", "path", "output directory (default is task directory)"),
    TASK_NAME('n', "name", " taskname", "task name (predefined instead of detecting)");

    /** Short option. */
    private final char shortName;
    /** Getter method for {@link #shortName}. */
    public char toChar() { return shortName; }

    /** Long option. */
    private final String longName;
    @Override
    public String toString() { return longName; }

    /** Argument name, if any. */
    private final String argument;
    /** Getter method for {@link #argument}. */
    public String getArgument() { return argument; }
    /** Checks if {@link #argument} is not null. */
    public boolean hasArgument() { return (argument != null); }

    /** User-friendly description. */
    private final String description;
    /** Getter method for {@link #description} */
    public String getDescription() { return description; }

    /** Basic constructor. */
    private Arguments(final char theShortName, final String theLongName, final String theArgument,
                         final String theDescription) {
        shortName = theShortName;
        longName = theLongName;
        argument = theArgument;
        description = theDescription;
    }

    /**
     * Commandline syntax usage line.
     */
    private static final String CMD_LINE_SYNTAX = "TaskConv {-h|[-d] <path>} [[-w] <path>] [options]";

    /**
     * List of options.
     */
    private static final Options options = new Options();
    static {
        for (Arguments option : Arguments.values()) {
            Option theOption = new Option(""+option.toChar(), ""+option, option.hasArgument(), option.getDescription());
            if (option.hasArgument()) {
                theOption.setArgName(option.getArgument());
            }
            options.addOption(theOption);
        }
    }

    /**
     * Print usage info and exit.
     */
    public static final void printUsage() {
        new HelpFormatter().printHelp(CMD_LINE_SYNTAX, options);
        System.exit(0);
    }

    /**
     * Parse command line.
     * @param args    commandline arguments
     * @return parsed command line object
     */
    public static final CommandLine parse(final String[] args) {
        CommandLine result = null;
        try {
            result = new PosixParser().parse(options, args);
            if (result.hasOption(HELP.toChar())) {
                printUsage();
            }
        } catch (ParseException e) {
            System.out.println(e.getLocalizedMessage());
            printUsage();
        }
        return result;
    }

}
