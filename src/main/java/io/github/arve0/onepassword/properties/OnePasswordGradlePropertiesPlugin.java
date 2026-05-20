package io.github.arve0.onepassword.properties;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.plugins.ExtraPropertiesExtension;

/**
 * A Gradle plugin that exposes 1Password secret references as lazy {@code Provider<String>}
 * extra properties.
 *
 * <p>For each project property whose value starts with {@code op://}, the plugin:
 * <ol>
 *   <li>Validates the reference eagerly (fail-fast at configuration time for obviously invalid refs).</li>
 *   <li>Registers a {@link Provider}{@code <String>} backed by {@link OpReadValueSource} in extra
 *       properties, without resolving the secret immediately.</li>
 * </ol>
 *
 * <p>Whether the secret ends up in the configuration cache depends on when the caller calls
 * {@code .get()} on the provider:
 * <ul>
 *   <li><b>Execution time</b> (e.g. inside {@code doLast}): secret is NOT stored in the cache;
 *       {@code op} is called once per build at execution time.</li>
 *   <li><b>Configuration time</b> (e.g. for repository credentials): Gradle fingerprints
 *       the value and stores it in the cache; {@code op} is called on every build for
 *       fingerprint validation.</li>
 * </ul>
 */
public final class OnePasswordGradlePropertiesPlugin implements Plugin<Project> {
    private static final String OP_PREFIX = "op://";

    @Override
    public void apply(Project project) {
        OpCliClient cli = OpCliClient.fromProject(project);
        project.getExtensions().create("onePassword", OnePasswordExtension.class, project, cli);
        ExtraPropertiesExtension extraProperties = project.getExtensions().getExtraProperties();

        project.getProperties().forEach((key, value) -> {
            if (!(value instanceof String stringValue) || !stringValue.startsWith(OP_PREFIX)) {
                return;
            }
            if (stringValue.length() <= OP_PREFIX.length()) {
                throw new PropertyResolutionException(
                        "Property '" + key + "' contains an invalid 1Password reference: '" + stringValue + "'."
                );
            }
            Provider<String> provider = project.getProviders().of(OpReadValueSource.class, spec -> {
                spec.getParameters().getReference().set(stringValue);
                spec.getParameters().getPropertyName().set(key);
                spec.getParameters().getCommand().set(cli.getCommand());
                spec.getParameters().getTimeoutMillis().set(cli.getTimeoutMillis());
            });
            extraProperties.set(key, provider);
        });
    }
}
