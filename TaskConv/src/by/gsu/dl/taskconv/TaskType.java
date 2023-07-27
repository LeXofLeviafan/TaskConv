package by.gsu.dl.taskconv;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Task type data.
 * @author Alexey Gulenko
 * @version 1.2
 */
public class TaskType implements Comparable<TaskType> {

    @Override
    public int compareTo(TaskType o) {
        return new Integer(infiles.length).compareTo(o.infiles.length);
    }

    /** Type configuration file lines info. */
    private enum TypeLines {
        INPUT_FILES(0),
        OUTPUT_FILES(1);
        private final int line;
        private TypeLines(int lineNumber) {
            line = lineNumber;
        }
        public int number() {
            return line;
        }
    }

    /** Type name. */
    private final String name;
    /** Pattern for input files. */
    private FilePattern infilePattern;
    /** Pattern for output files. */
    private FilePattern outfilePattern;
    /** Detected input files. */
    private FilePattern.FileProcessed[] infiles;
    /** Getter method for {@link #infiles} */
    public FilePattern.FileProcessed[] getInfiles() { return infiles; }
    /** Detected output files. */
    private FilePattern.FileProcessed[] outfiles;
    /** Getter method for {@link #outfiles} */
    public FilePattern.FileProcessed[] getOutfiles() { return outfiles; }

    /**
     * Private constructor.
     * @param theName    type name
     */
    private TaskType(final String theName) {
        name = theName;
    }

    /**
     * Factory method that produces data for task type.
     * @param path      path to task type file
     * @param config    configuration
     * @param files     set of files to match
     * @return TaskType instance for given type, if it's valid
     */
    public static TaskType process(final File path, final Config config, final Collection<File> files) {
        TaskType type = new TaskType(path.getName());
        if (config.isVerboseOn()) {
            System.out.println("Checking task type \"" + type.name + "\"...");
        }
        try {
            List<String> lines = Files.readAllLines(path.toPath(), Charset.defaultCharset());
            //System.out.println(lines);
            type.infilePattern = FilePattern.process(lines.get(TypeLines.INPUT_FILES.number()), config);
            type.outfilePattern = FilePattern.process(lines.get(TypeLines.OUTPUT_FILES.number()), config);
            if (type.infilePattern == null || type.outfilePattern == null) {
                System.err.println("Incorrect task type: \"" + type.name + "\".");
                return null;
            }
        } catch (IOException e) {
            config.rageExit("Failed to read task type: \"" + type.name + "\".");
            return null;
        }
        if (!type.calcInOutFiles(config, files)) {
            return null;
        }
        if (!type.checkInOutFiles(config)) {
            return null;
        }
        if (config.isVerboseOn()) {
            System.out.println("Task type \"" + type.name + "\": passed.");
        }
        return type;
    }

    /**
     * Calculates the input/output files set.
     * @param config    configuration
     * @param files     set of files to match
     * @return true if found matching files, false otherwise
     */
    private boolean calcInOutFiles(final Config config, final Collection<File> files) {
        List<FilePattern.FileProcessed>
                ins = new ArrayList<FilePattern.FileProcessed>(),
                outs = new ArrayList<FilePattern.FileProcessed>();
        int prefix = config.getTaskDir().length();
        for (File file : files) {
            String path = file.toString().substring(prefix);
            FilePattern.FileProcessed in = infilePattern.match(path);
            FilePattern.FileProcessed out = null;
            if (!config.checkInfilesOnly()) {
                out = outfilePattern.match(path);
            }
            if (in != null && out != null) {
                if (config.isVerboseOn()) {
                    System.err.println("File \"" + path + "\" is detected as both input and output file for type \""
                            + name + "\".");
                }
                return false;
            }
            if (in != null) {
                ins.add(in);
            }
            if (out != null) {
                outs.add(out);
            }
        }
        if (ins.size() == 0) {
            return false;
        }
        Collections.sort(ins);
        if (outs.size() > 0) {
            Collections.sort(outs);
        }
        infiles = ins.toArray(new FilePattern.FileProcessed[0]);
        outfiles = outs.toArray(new FilePattern.FileProcessed[0]);
        return true;
    }

    /**
     * Checks the input/output files set for existence and collisions.
     * @param config    configuration
     * @return true if files can be moved, false otherwise
     */
    private boolean checkFileSet(final Config config) {
        final String workDir = config.getWorkDir();
        Set<String> allFiles = new HashSet<String>();
        for (int i = 0; i < infiles.length; i++) {
            if (!allFiles.add(infiles[i].path.toString())) {
                return false;
            }
            if (Files.exists(Paths.get(workDir + (i+1) + ".in"))) {
                return false;
            }
        }
        for (int i = 0; i < outfiles.length; i++) {
            if (!allFiles.add(outfiles[i].path.toString())) {
                return false;
            }
            if (Files.exists(Paths.get(workDir + (i+1) + ".out"))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the input/output file set is valid.
     * @param config    configuration
     * @return true the set is valid, false otherwise
     */
    private boolean checkInOutFiles(final Config config) {
        if (!checkFileSet(config)) {
            return false;
        }
        if (config.checkInfilesOnly()) {
            String taskName = infiles[0].taskName;
            for (FilePattern.FileProcessed file : infiles) {
                if (!taskName.equals(file.taskName)) {
                    System.err.println("Task names don't match for type \"" + name + "\".");
                    return false;
                }
            }
            return true;
        }
        if (infiles.length != outfiles.length) {
            if (config.isVerboseOn()) {
                System.out.println("Number of input and output files doesn't match for type \"" + name + "\".");
            }
            return false;
        }
        String taskName = infiles[0].taskName;
        String taskName2 = outfiles[0].taskName;
        if (!taskName.equals("") && !taskName2.equals("")) {
            if (!taskName.equals(taskName2)) {
                System.err.println("Task names don't match for type \"" + name + "\".");
                return false;
            }
        }
        for (int i = 0; i < infiles.length; i++) {
            if (!taskName.equals(infiles[i].taskName) || !taskName2.equals(outfiles[i].taskName)) {
                System.err.println("Task names don't match for type \"" + name + "\".");
                return false;
            }
            if (!infiles[i].groupNumber.equals(outfiles[i].groupNumber)) {
                System.err.println("Group numbers don't match for type \"" + name + "\".");
                return false;
            }
            if (!infiles[i].testNumber.equals(outfiles[i].testNumber)) {
                System.err.println("Test numbers don't match for type \"" + name + "\".");
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("(%3d) %s", infiles.length, name);
    }

    /** Getter method for {@link #name} */
    public String getName() {
        return name;
    }

}
