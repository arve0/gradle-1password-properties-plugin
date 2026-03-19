package io.github.arve0.onepassword.properties;

/**
 * An exception that indicates an error occurred during the resolution of a property.
 *
 * The {@code PropertyResolutionException} class extends {@link RuntimeException} and
 * provides constructors to represent both simple and wrapped exceptions with descriptive messages.
 *
 * This exception is intended to signal issues that occur when resolving a property,
 * such as invalid configurations, missing values, or other related errors.
 */
public final class PropertyResolutionException extends RuntimeException {
    /**
     * Constructs a new {@code PropertyResolutionException} with the specified detail message.
     *
     * This constructor is used to create an instance of {@code PropertyResolutionException}
     * when a property resolution error occurs, allowing the error to be described with a
     * specific message providing additional context.
     *
     * @param message the detail message explaining the nature of the property resolution issue.
     */
    public PropertyResolutionException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code PropertyResolutionException} with the specified detail message
     * and cause.
     *
     * This constructor is used to create an instance of {@code PropertyResolutionException}
     * to represent a specific property resolution error, wrapping the underlying cause for
     * additional context.
     *
     * @param message the detail message explaining the nature of the property resolution issue.
     * @param cause the cause of the exception, typically another exception that led to this error.
     */
    public PropertyResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
