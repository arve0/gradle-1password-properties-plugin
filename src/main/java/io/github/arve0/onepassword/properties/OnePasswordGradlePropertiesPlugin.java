package io.github.arve0.onepassword.properties;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;

/**
 * A Gradle plugin that resolves project properties containing references to 1Password secrets.
 *
 * This plugin scans through all project properties, and if a property value begins with the
 * 1Password-specific prefix ("op://"), it is resolved by communicating with the 1Password CLI.
 * Resolved secrets are then stored in the Gradle project's extra properties, making them
 * accessible like regular project properties.
 *
 * The plugin uses the following components:
 * - {@link OpCliClient}: Interfaces with the 1Password CLI to resolve secret references.
 * - {@link ProjectPropertyResolver}: Handles the logic for interpreting and resolving property values.
 *
 * Notes:
 * - The plugin assumes the 1Password CLI is installed and available in the system PATH.
 * - Invalid or unresolvable secrets with the "op://" prefix will result in errors being thrown.
 * - The CLI command and timeout configuration can be overridden using specific project properties.
 *
 * Responsibilities:
 * - Iterates through all defined project properties.
 * - Checks if the property value is a String and starts with the "op://" prefix.
 * - Resolves valid 1Password references and stores the resolved value in the project's extra properties.
 */
public final class OnePasswordGradlePropertiesPlugin implements Plugin<Project> {
    private static final String OP_PREFIX = "op://";

    @Override
    public void apply(Project project) {
        ProjectPropertyResolver resolver = new ProjectPropertyResolver(OpCliClient.fromProject(project));
        ExtraPropertiesExtension extraProperties = project.getExtensions().getExtraProperties();
        project.getProperties().forEach((key, value) -> {
            if (!(value instanceof String stringValue) || !stringValue.startsWith(OP_PREFIX)) {
                return;
            }
            String resolved = resolver.resolve(key, stringValue);
            extraProperties.set(key, resolved);
        });
    }
}
