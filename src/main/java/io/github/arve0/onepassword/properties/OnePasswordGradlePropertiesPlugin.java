package io.github.arve0.onepassword.properties;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.plugins.ExtraPropertiesExtension;
/**
 * A Gradle plugin that exposes 1Password secret references as lazy {@code Provider<String>}
 * properties.
 *
 * Usage, build.gradle.kts:
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
        SecretCacheBuildService cacheService = project.getGradle().getSharedServices()
                .registerIfAbsent(SecretCacheBuildService.SERVICE_NAME, SecretCacheBuildService.class, spec -> {})
                .get();
        SecretCacheBuildService.activate(cacheService);
        OpCliClient cli = OpCliClient.fromProject(project);
        project.getExtensions().create("onePassword", OnePasswordExtension.class, project, cli);
    }
}
