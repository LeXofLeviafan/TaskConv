package by.gsu.dl.taskconv;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File pattern for task types.
 * @author Alexey Gulenko
 * @version 1.2
 */
public class FilePattern {

    /** File pattern elements. */
    public enum Elements {
        TASK_NAME("TaskName"),
        GROUP_NUMBER("S"),
        TEST_NUMBER("SS"),
        TEST_LETTER("SL");

        private final String value;
        private Elements(final String theValue) {
            value = theValue;
        }
        @Override
        public String toString() {
            return value;
        }
    }

    /** Alphabet (for converting letters to indices). */
    private static final String ABC;
    static {
        StringBuffer abc = new StringBuffer();
        for (char c = 'a'; c <= 'z'; c++) {
            abc.append(c);
        }
        ABC = abc.toString();
    }

    /** Base pattern from configuration file. */
    private final String basePattern;
    /** String data on the processed pattern. */
    private final String[] patternData;
    /** Compiled regular expression. */
    private final Pattern regexp;

    /**
     * Private constructor.
     * @param theBasePattern    base pattern from configuration file
     * @param thePatternData    string data on the processed pattern
     */
    private FilePattern(final String theBasePattern, final String[] thePatternData) {
        basePattern = theBasePattern;
        patternData = thePatternData;
        regexp = Pattern.compile(patternData[0]);
    }

    /**
     * Factory method for generating file patterns.
     * @param pattern base pattern from task type file
     * @param config configuration
     * @return FilePattern instance if the pattern is correct, null otherwise
     */
    public static FilePattern process(final String pattern, final Config config) {
        String[] patternData = makeRegexPattern(pattern, config);
        if (patternData == null) {
            System.err.println("Pattern is incorrect: \"" + pattern + "\".");
            return null;
        }
        return new FilePattern(pattern, patternData);
    }

    /**
     * Generates data from input files.
     * @param basePattern base pattern
     * @param config config data
     * @return array: [0] is regexp, others are group names
     */
    private static String[] makeRegexPattern(final String basePattern, final Config config) {
        String remains = basePattern;
        List<String> result = new ArrayList<String>();
        result.add("(?i)");
        while (!remains.isEmpty()) {
            String[] tokens = splitToken(remains, config);
            if (tokens == null) {
                return null;
            }
            String token = tokens[0], groupName = tokens[1], after = tokens[2];
            if (groupName != null) {
                result.add(groupName);
            }
            result.set(0, result.get(0) + token);
            remains = after;
        }
        return result.toArray(new String[0]);
    }

    /**
     * Split string into token, group and leftover part.
     * @param s         string tokenized
     * @param config    configuration
     * @return {token, group, groupName, leftover}
     */
    private static String[] splitToken(final String s, final Config config) {
        String token, group, groupName, after;
        int tokenLength = s.indexOf("${");
        int tokenLength1 = s.indexOf("$[");
        char groupLimiter = '}';
        boolean groupRequired = true;
        if (tokenLength < 0 || (tokenLength1 >= 0 && tokenLength1 < tokenLength)) {
            tokenLength = tokenLength1;
            groupLimiter = ']';
            groupRequired = false;
        }
        if (tokenLength < 0) {
            return new String[] {Pattern.quote(s), null, ""};
        }
        after = s.substring(tokenLength + "${".length());
        int groupLength = after.indexOf(groupLimiter);
        if (groupLength < 0) {
            return null;
        }
        token = Pattern.quote(s.substring(0, tokenLength));
        groupName = after.substring(0, groupLength);
        group = calcGroup(groupName, groupRequired, config);
        if (group == null) {
            return null;
        }
        after = after.substring(groupLength + "}".length());
        return new String[] {token + group, groupName, after};
    }

    /**
     * Generate regex group.
     * @param groupName        group name
     * @param groupRequired    group match required
     * @param config           configuration
     * @return group regexp
     */
    public static String calcGroup(final String groupName, final boolean groupRequired, final Config config) {
        String group = "";
        Elements element = null;
        for (Elements item : Elements.values()) {
            if (groupName.equals(item.toString())) {
                element = item;
            }
        }
        if (element == null) {
            return null;
        }
        if (groupRequired) {
            switch (element) {
                case TASK_NAME:
                    group = config.getTaskName();
                    break;
                case GROUP_NUMBER:
                case TEST_NUMBER:
                    group = "\\d+";
                    break;
                case TEST_LETTER:
                    group = '[' + Config.LETTERS + "]";
                    break;
            }
        } else {
            switch (element) {
                case TEST_NUMBER:
                    group = "\\d*";
                    break;
                case TEST_LETTER:
                    group = '[' + Config.LETTERS + "]?";
                    break;
                default:
                    return null;
            }
        }
        return '(' + group + ')';
    }

    @Override
    public String toString() {
        return basePattern;
    }

    /** Detected file data structure. */
    public class FileProcessed implements Comparable<FileProcessed> {
        /** Path to the file. */
        public final String path;
        /** Detected task name. */
        public final String taskName;
        /** Detected group number. */
        public final Integer groupNumber;
        /** Detected test number (in group). */
        public final Integer testNumber;

        /**
         * Constructor.
         * @param thePath           path to the file
         * @param theTaskName       detected task name
         * @param theGroupNumber    detected group number
         * @param theTestNumber     detected test number (in group)
         */
        public FileProcessed(final String thePath, final String theTaskName, final int theGroupNumber,
                             final int theTestNumber) {
            path = thePath;
            taskName = theTaskName;
            groupNumber = theGroupNumber;
            testNumber = theTestNumber;
        }

        @Override
        public int compareTo(FileProcessed o) {
            if (groupNumber != o.groupNumber) {
                return groupNumber.compareTo(o.groupNumber);
            }
            return testNumber.compareTo(o.testNumber);
        }
    }

    /**
     * Matches filename with template and parses it.
     * @param filename    name of the file to match
     * @return FileProcessed instance if file was matched properly, null otherwise
     */
    public FileProcessed match(final String filename) {
        Matcher matcher = regexp.matcher(filename);
        if (!matcher.matches()) {
            return null;
        }
        String taskName = null;
        Integer groupNumber = null;
        Integer testNumber = null;
        for (int i = 1; i < patternData.length; i++) {
            Elements element = null;
            for (Elements item : Elements.values()) {
                if (patternData[i].equals(item.toString())) {
                    element = item;
                }
            }
            String s = matcher.group(i);
            try {
                switch (element) {
                    case TASK_NAME:
                        taskName = getValue(taskName, matcher.group(i));
                        if (taskName == null) {
                            return null;
                        }
                        break;
                    case GROUP_NUMBER:
                        groupNumber = getValue(groupNumber, Integer.parseInt(s));
                        if (groupNumber == null) {
                            return null;
                        }
                        break;
                    case TEST_NUMBER:
                        testNumber = getValue(testNumber, Integer.parseInt(s));
                        if (testNumber == null) {
                            return null;
                        }
                        break;
                    case TEST_LETTER:
                        testNumber = getValue(testNumber, ABC.indexOf(s)+1);
                        if (testNumber == null) {
                            return null;
                        }
                        break;
                }
            } catch (NumberFormatException e) {
                //
            }
        }
        if (taskName == null) {
            taskName = "";
        }
        if (groupNumber == null) {
            return null;
        }
        if (testNumber == null) {
            testNumber = 1;
        }
        return new FileProcessed(filename, taskName, groupNumber, testNumber);
    }

    /**
     * Checks if new value differs from the old one.
     * @param oldValue    old value (null if not set)
     * @param newValue    new value
     * @param <T>         parameter class
     * @return new value if it doesn't conflict with the old one, null otherwise
     */
    private <T> T getValue(T oldValue, T newValue) {
        if (oldValue != null && !oldValue.equals(newValue)) {
            return null;
        }
        return newValue;
    }

}
