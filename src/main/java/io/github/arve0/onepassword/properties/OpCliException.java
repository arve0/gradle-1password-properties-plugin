package io.github.arve0.onepassword.properties;

/**
 * An exception that indicates an error occurred while interacting with the 1Password CLI.
 *
 * <p>The OpCliException class extends {@link RuntimeException} and provides constructors
 * to represent both simple and wrapped exceptions with descriptive messages.
 *
 * <p>This exception is primarily used in the context of the 1Password CLI integration
 * to signal issues such as command execution errors, timeouts, or unexpected output.
 */
public final class OpCliException extends RuntimeException {
    /**
     * Constructs a new {@code OpCliException} with the specified detail message.
     *
     * This constructor can be used to create an instance of {@code OpCliException}
     * with a descriptive error message indicating the issue that occurred while
     * interacting with the 1Password CLI.
     *
     * @param message the detail message providing additional context or information
     *                about the exception being thrown.
     */
    public OpCliException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code OpCliException} with the specified detail message and cause.
     *
     * This constructor is used to create an instance of {@code OpCliException}
     * when an underlying cause (another throwable) needs to be wrapped alongside
     * a descriptive error message. This is particularly useful in scenarios where
     * exceptions related to the 1Password CLI need to be contextualized with additional
     * information about the error.
     *
     * @param message the detail message providing additional context or information
     *                about the exception being thrown.
     * @param cause   the underlying cause of the exception, typically another
     *                {@link Throwable} instance that provides more details about
     *                the root issue.
     */
    public OpCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
