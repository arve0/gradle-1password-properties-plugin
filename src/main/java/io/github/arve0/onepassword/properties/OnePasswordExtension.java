package io.github.arve0.onepassword.properties;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/**
 * Extension that provides a unified API to access project properties as lazy {@code Provider<String>}.
 *
 * <p>Access via {@code onePassword.property("KEY")} in any build script where this plugin is applied.
 * The returned provider resolves identically regardless of whether the value came from
 * {@code gradle.properties}, an {@code ORG_GRADLE_PROJECT_*} environment variable, or a {@code -P} flag.
 *
 * <ul>
 *   <li>Plain string values are wrapped in an eager {@code Provider<String>}.</li>
 *   <li>{@code op://} references are resolved lazily via the 1Password CLI.</li>
 * </ul>
 */
public class OnePasswordExtension {
    private static final String OP_PREFIX = "op://";

    private final Project project;
    private final OpCliClient cli;

    public OnePasswordExtension(Project project, OpCliClient cli) {
        this.project = project;
        this.cli = cli;
    }

    /**
     * Returns a {@code Provider<String>} for the given project property key.
     *
     * <p>The returned provider behaves the same regardless of the property source:
     * {@code gradle.properties}, environment variable ({@code ORG_GRADLE_PROJECT_*}), or {@code -P}.
     *
     * <p>If the property value starts with {@code op://}, the provider resolves the secret via
     * the 1Password CLI when {@code .get()} is called.
     * For plain string values, the provider returns the value directly.
     *
     * @param key the project property key
     * @return a {@code Provider<String>} for the property value
     * @throws PropertyResolutionException if the property is not set
     */
    public Provider<String> property(String key) {
        Object value = project.findProperty(key);
        if (value == null) {
            throw new PropertyResolutionException("Property '" + key + "' is not set.");
        }
        if (value instanceof Provider) {
            @SuppressWarnings("unchecked")
            Provider<String> provider = (Provider<String>) value;
            return provider;
        }
        if (value instanceof String stringValue) {
            if (stringValue.startsWith(OP_PREFIX)) {
                if (stringValue.length() <= OP_PREFIX.length()) {
                    throw new PropertyResolutionException(
                            "Property '" + key + "' contains an invalid 1Password reference: '" + stringValue + "'."
                    );
                }
                return project.getProviders().of(OpReadValueSource.class, spec -> {
                    spec.getParameters().getReference().set(stringValue);
                    spec.getParameters().getPropertyName().set(key);
                    spec.getParameters().getCommand().set(cli.getCommand());
                    spec.getParameters().getTimeoutMillis().set(cli.getTimeoutMillis());
                });
            }
            return project.getProviders().provider(() -> stringValue);
        }
        throw new PropertyResolutionException(
                "Property '" + key + "' must be a String but was " + value.getClass().getSimpleName() + "."
        );
    }
}
