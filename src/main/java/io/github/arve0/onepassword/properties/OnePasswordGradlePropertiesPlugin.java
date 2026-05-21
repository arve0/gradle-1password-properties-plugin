package io.github.arve0.onepassword.properties;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.plugins.ExtraPropertiesExtension;
/**
 * A Gradle plugin that resolves {@code op://} project properties via the 1Password CLI and
 * exposes them as lazy {@code Provider<String>} extra properties.
 *
 * <p>At plugin init, every project property whose value starts with {@code op://} is registered
 * as a lazy {@link org.gradle.api.provider.Provider}{@code <String>} in extra properties.
 * Plain string properties are left unchanged. The {@code onePassword} extension is then
 * registered to give build scripts a typed API for accessing any property as a provider.
 *
 * <p>Usage, {@code build.gradle.kts}:
 * <pre>
 * plugins {
 *  id("io.github.arve0.onepassword.properties")
 * }
 *
 * tasks.register("printSecret") {
 *   val secret: Provider&lt;String&gt; = onePassword.property("MY_SECRET")
 *   doLast {
 *     println("Secret is: ${secret.get()}")
 *   }
 * }
 * </pre>
 *
 * <h2>Configuration Cache</h2>
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

        project.getExtensions().create("onePassword", OnePasswordExtension.class, project, cli);
    }
}
