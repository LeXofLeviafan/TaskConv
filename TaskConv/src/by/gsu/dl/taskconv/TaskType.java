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
 * @version 1.2.1
 */
public class TaskType implements Comparable<TaskType> {

    @Override
    public int compareTo(TaskType o) {
        int result = filesNum.compareTo(o.filesNum);
        for (int i = 0; i < inFiles.length && result == 0; i++) {
            result = new Integer(inFiles[i].length).compareTo(o.inFiles[i].length);
        }
        return result;
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
    /** Detected input files, per task. */
    private FilePattern.FileProcessed[][] inFiles;
    /** Getter method for {@link #inFiles} */
    public FilePattern.FileProcessed[][] getInFiles() { return inFiles; }
    /** Detected output files, per task. */
    private FilePattern.FileProcessed[][] outFiles;
    /** Getter method for {@link #outFiles} */
    public FilePattern.FileProcessed[][] getOutFiles() { return outFiles; }
    /** Total number of detected files. */
    private Integer filesNum;

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
    public static TaskType process(final File path, final Config config, final String[][] files) {
        TaskType type = new TaskType(path.getName());
        config.verboseMessage("Checking task type \"" + type.name + "\"...");
        try {
            List<String> lines = Files.readAllLines(path.toPath(), Charset.defaultCharset());
            //System.out.println(lines);
            type.infilePattern = FilePattern.process(lines.get(TypeLines.INPUT_FILES.number()), config);
            type.outfilePattern = FilePattern.process(lines.get(TypeLines.OUTPUT_FILES.number()), config);
            if (type.infilePattern == null || type.outfilePattern == null) {
                System.out.println("Incorrect task type: \"" + type.name + "\".");
                return null;
            }
        } catch (IOException e) {
            config.rageExit("Failed to read task type: \"" + type.name + "\".");
            return null;
        }
        if (!type.calcInOutFiles(config, files)) {
            config.verboseMessage("Failed to gather files.");
            return null;
        }
        if (!type.checkInOutFiles(config)) {
            return null;
        }
        config.verboseMessage("Task type \"" + type.name + "\": passed.");
        return type;
    }

    /**
     * Calculates the input/output files set.
     * @param config    configuration
     * @param files     set of files to match
     * @return true if found matching files, false otherwise
     */
    private boolean calcInOutFiles(final Config config, final String[][] files) {
        List<FilePattern.FileProcessed[]>
                ins = new ArrayList<FilePattern.FileProcessed[]>(),
                outs = new ArrayList<FilePattern.FileProcessed[]>();
        int prefix = config.getTaskDir().length();
        filesNum = 0;
        for (int task = 0; task < files.length; task++) {
            List<FilePattern.FileProcessed>
                    taskIns = new ArrayList<FilePattern.FileProcessed>(),
                    taskOuts = new ArrayList<FilePattern.FileProcessed>();
            int taskPrefix = config.getTasks()[task].length();
            for (String file : files[task]) {
                String path = file.substring(prefix + taskPrefix);
                FilePattern.FileProcessed in = infilePattern.match(path);
                FilePattern.FileProcessed out = null;
                if (!config.checkInfilesOnly()) {
                    out = outfilePattern.match(path);
                }
                if (in != null && out != null) {
                    config.verboseMessage("File \"" + path +
                            "\" is detected as both input and output file for type \"" + name + "\".");
                    return false;
                }
                if (in != null) {
                    taskIns.add(in);
                }
                if (out != null) {
                    taskOuts.add(out);
                }
            }
            if (taskIns.size() == 0) {
                return false;
            }
            Collections.sort(taskIns);
            if (taskOuts.size() > 0) {
                Collections.sort(taskOuts);
            }
            ins.add(taskIns.toArray(new FilePattern.FileProcessed[0]));
            outs.add(taskOuts.toArray(new FilePattern.FileProcessed[0]));
            filesNum += taskIns.size();
        }
        inFiles = ins.toArray(new FilePattern.FileProcessed[0][0]);
        outFiles = outs.toArray(new FilePattern.FileProcessed[0][0]);
        return true;
    }

    /**
     * Checks the input/output files set for existence and collisions.
     * @param config    configuration
     * @return true if files can be moved, false otherwise
     */
    private boolean checkFileSet(final Config config) {
        final String workDir = config.getWorkDir();
        final String[] tasks = config.getTasks();
        for (int task = 0; task < inFiles.length; task++) {
            Set<String> allFiles = new HashSet<String>();
            for (int i = 0; i < inFiles[task].length; i++) {
                if (!allFiles.add(inFiles[task][i].path.toString())) {
                    return false;
                }
                if (Files.exists(Paths.get(workDir + tasks[task] + (i+1) + ".in"))) {
                    return false;
                }
            }
            for (int i = 0; i < outFiles[task].length; i++) {
                if (!allFiles.add(outFiles[task][i].path.toString())) {
                    return false;
                }
                if (Files.exists(Paths.get(workDir + tasks[task] + (i+1) + ".out"))) {
                    return false;
                }
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
            config.verboseMessage("File set consistency check failed.");
            return false;
        }
        for (int task = 0; task < inFiles.length; task++) {
            if (config.checkInfilesOnly()) {
                String taskName = inFiles[task][0].taskName;
                for (FilePattern.FileProcessed file : inFiles[task]) {
                    if (!taskName.equals(file.taskName)) {
                        config.verboseMessage("Task names don't match for type \"" + name + "\".");
                        return false;
                    }
                }
                continue;
            }
            if (inFiles.length != outFiles.length) {
                config.verboseMessage("Number of input and output files doesn't match for type \"" + name + "\".");
                return false;
            }
            String taskName = inFiles[task][0].taskName;
            String taskName2 = outFiles[task][0].taskName;
            if (!taskName.equals("") && !taskName2.equals("")) {
                if (!taskName.equals(taskName2)) {
                    config.verboseMessage("Task names don't match for type \"" + name + "\".");
                    return false;
                }
            }
            for (int i = 0; i < inFiles[task].length; i++) {
                if (!taskName.equals(inFiles[task][i].taskName) || !taskName2.equals(outFiles[task][i].taskName)) {
                    config.verboseMessage("Task names don't match for type \"" + name + "\".");
                    return false;
                }
                if (!inFiles[task][i].groupNumber.equals(outFiles[task][i].groupNumber)) {
                    config.verboseMessage("Group numbers don't match for type \"" + name + "\".");
                    return false;
                }
                if (!inFiles[task][i].testNumber.equals(outFiles[task][i].testNumber)) {
                    config.verboseMessage("Test numbers don't match for type \"" + name + "\".");
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(String.format("(%03d) ", filesNum));
        if (inFiles.length > 1) {
            result.append(String.format("{%02d", inFiles[0].length));
            for (int i = 1; i < inFiles.length; i++) {
                result.append(String.format(", %02d", inFiles[i].length));
            }
            result.append("} ");
        }
        result.append(name);
        return result.toString();
    }

    /** Getter method for {@link #name} */
    public String getName() {
        return name;
    }

}
