package io.github.arve0.onepassword.properties;

final class ProjectPropertyResolver {
    private static final String OP_PREFIX = "op://";
    private final SecretReferenceReader secretReferenceReader;

    ProjectPropertyResolver(SecretReferenceReader secretReferenceReader) {
        this.secretReferenceReader = secretReferenceReader;
    }

    String resolve(String propertyKey, Object value) {
        if (value == null) {
            throw new PropertyResolutionException("Property '" + propertyKey + "' is missing.");
        }
        if (!(value instanceof String stringValue)) {
            throw new PropertyResolutionException(
                    "Property '" + propertyKey + "' must be a String but was "
                            + value.getClass().getSimpleName() + "."
            );
        }
        if (!stringValue.startsWith(OP_PREFIX)) {
            return stringValue;
        }
        if (stringValue.length() <= OP_PREFIX.length()) {
            throw new PropertyResolutionException(
                    "Property '" + propertyKey + "' contains an invalid 1Password reference: '" + stringValue + "'."
            );
        }
        try {
            return secretReferenceReader.read(stringValue);
        } catch (OpCliException e) {
            throw new PropertyResolutionException(
                    "Failed to resolve property '" + propertyKey + "' via 1Password: " + e.getMessage(),
                    e
            );
        }
    }
}
