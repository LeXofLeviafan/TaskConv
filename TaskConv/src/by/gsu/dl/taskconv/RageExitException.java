package by.gsu.dl.taskconv;

/**
 * Exception used to stop execution.
 * @author Alexey Gulenko
 * @version 1.2.2
 */
public class RageExitException extends Exception {
    RageExitException (String message) {
        super(message);
    }
}
