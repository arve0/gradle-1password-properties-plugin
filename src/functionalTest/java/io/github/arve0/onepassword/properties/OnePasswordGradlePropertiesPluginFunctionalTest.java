package io.github.arve0.onepassword.properties;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OnePasswordGradlePropertiesPluginFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void resolvesProjectPropertyFromOnePasswordReference() throws IOException {
        assumePosix();
        Path opMock = createOpMock("echo \"functional-secret\"");
        writeProjectFiles(opMock, "TOKEN=op://vault/item/field");

        BuildResult result = runBuild("printToken");

        assertOutputContains(result, "TOKEN=functional-secret", "resolved token should be printed");
    }

    @Test
    void surfacesInvalidReferenceWithPropertyContext() throws IOException {
        assumePosix();
        Path opMock = createOpMock("echo \"ignored\"");
        writeProjectFiles(opMock, "TOKEN=op://");

        UnexpectedBuildFailure failure = assertThrows(
                UnexpectedBuildFailure.class,
                () -> runBuild("printToken")
        );

        assertMessageContains(failure.getMessage(), "Property 'TOKEN'", "failure should include property context");
        assertMessageContains(failure.getMessage(), "invalid 1Password reference", "failure should include validation reason");
    }

    @Test
    void reusesConfigurationCacheWhenSecretUnchanged() throws IOException {
        assumePosix();
        Path secretFile = projectDir.resolve("secret.txt");
        Path invocationCountFile = projectDir.resolve("op-invocations.txt");
        Files.writeString(secretFile, "functional-secret\n");
        Path opMock = createStatefulOpMock(secretFile, invocationCountFile);
        writeProjectFiles(opMock, "TOKEN=op://vault/item/field");

        BuildResult firstRun = runBuildWithConfigurationCache("printToken");
        Integer initialInvocationCount = readInvocationCount(invocationCountFile);

        BuildResult secondRun = runBuildWithConfigurationCache("printToken");
        assertEquals(initialInvocationCount, readInvocationCount(invocationCountFile));

        assertOutputContains(firstRun, "TOKEN=functional-secret", "first run should resolve token");
        assertOutputContains(secondRun, "TOKEN=functional-secret", "second run should still print the same token");
        assertOutputContains(secondRun, "Configuration cache entry reused", "second run should reuse configuration cache");
    }

    @Test
    void doNotInvalidateConfigurationCacheWhenSecretChanges() throws IOException {
        assumePosix();
        Path secretFile = projectDir.resolve("secret.txt");
        Path invocationCountFile = projectDir.resolve("op-invocations.txt");
        Files.writeString(secretFile, "functional-secret\n");
        Path opMock = createStatefulOpMock(secretFile, invocationCountFile);
        writeProjectFiles(opMock, "TOKEN=op://vault/item/field");
        assertEquals(0, readInvocationCount(invocationCountFile));

        BuildResult firstRun = runBuildWithConfigurationCache("printToken");
        Integer initialInvocationCount = readInvocationCount(invocationCountFile);
        assertOutputContains(firstRun, "TOKEN=functional-secret", "first run should resolve initial token");

        Files.writeString(secretFile, "changed-secret\n");

        BuildResult secondRun = runBuildWithConfigurationCache("printToken");
        assertEquals(initialInvocationCount, readInvocationCount(invocationCountFile));

        assertOutputContains(secondRun, "TOKEN=functional-secret", "second run should use cached token even after secret change");
        assertOutputContains(secondRun, "Configuration cache entry reused", "second run should reuse cached configuration");
    }

    @Test
    void opIsNotCalledWhenConfigurationCacheHasSecret() throws IOException {
        assumePosix();
        Path secretFile = projectDir.resolve("secret.txt");
        Path invocationCountFile = projectDir.resolve("op-invocations.txt");
        Files.writeString(secretFile, "functional-secret\n");
        Path opMock = createStatefulOpMock(secretFile, invocationCountFile);
        writeProjectFiles(opMock, "TOKEN=op://vault/item/field");

        BuildResult firstRun = runBuildWithConfigurationCache("printToken");
        Integer initialInvocationCount = readInvocationCount(invocationCountFile);

        assertEquals(1, initialInvocationCount);
        assertOutputContains(firstRun, "TOKEN=functional-secret", "first run should resolve token");

        Files.writeString(
                opMock,
                "#!/usr/bin/env bash\n" +
                        "set -euo pipefail\n" +
                        "echo 'op should not be called when configuration cache is reused' >&2\n" +
                        "exit 99\n"
        );
        Files.setPosixFilePermissions(
                opMock,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                )
        );

        BuildResult secondRun = runBuildWithConfigurationCache("printToken");
        assertEquals(initialInvocationCount, readInvocationCount(invocationCountFile));

        assertOutputContains(secondRun, "TOKEN=functional-secret", "second run should use cached token");
        assertOutputContains(secondRun, "Configuration cache entry reused", "second run should reuse configuration cache");
    }

    @Test
    void invalidationCacheWithNoConfigurationCacheGradleArgumentWillReadChangedSecretFromOp() throws IOException {
        assumePosix();
        Path secretFile = projectDir.resolve("secret.txt");
        Path invocationCountFile = projectDir.resolve("op-invocations.txt");
        Files.writeString(secretFile, "functional-secret\n");
        Path opMock = createStatefulOpMock(secretFile, invocationCountFile);
        writeProjectFiles(opMock, "TOKEN=op://vault/item/field");

        BuildResult firstRun = runBuild("printToken");
        Integer initialInvocationCount = readInvocationCount(invocationCountFile);

        assertEquals(1, initialInvocationCount);
        assertOutputContains(firstRun, "TOKEN=functional-secret", "first run should resolve initial token");

        Files.writeString(secretFile, "changed-secret\n");
        BuildResult secondRun = runBuild("printToken");

        assertOutputContains(secondRun, "TOKEN=changed-secret", "second run should resolve changed secret without configuration cache");
        assertEquals(initialInvocationCount + 1, readInvocationCount(invocationCountFile));
    }

    @Test
    void configurationCacheStoresWithoutProblems() throws IOException {
        assumePosix();
        Path opMock = createOpMock("echo \"functional-secret\"");
        writeProjectFiles(opMock, "TOKEN=op://vault/item/field");

        BuildResult result = runBuildWithConfigurationCache("printToken");

        assertOutputContains(result, "TOKEN=functional-secret", "resolved token should be printed");
        assertOutputDoesNotMatchPattern(result, "problem.*configuration cache",
                "configuration cache should store without problems");
    }

    private void writeProjectFiles(Path opMock, String tokenProperty) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"functional-test\"\n");
        Files.writeString(
                projectDir.resolve("gradle.properties"),
                tokenProperty + "\n" +
                        "onePassword.op.command=" + opMock + "\n"
        );
        Files.writeString(
                projectDir.resolve("build.gradle.kts"),
                "plugins {\n" +
                        "    id(\"io.github.arve0.1password.properties\")\n" +
                        "}\n" +
                        "\n" +
                        "tasks.register(\"printToken\") {\n" +
                        "    val token = project.property(\"TOKEN\").toString()\n" +
                        "    doLast {\n" +
                        "        println(\"TOKEN=$token\")\n" +
                        "    }\n" +
                        "}\n"
        );
    }

    private BuildResult runBuild(String taskName) {
        return gradleRunner(taskName, "--stacktrace").build();
    }

    private BuildResult runBuildWithConfigurationCache(String taskName) {
        return gradleRunner(taskName, "--configuration-cache", "--configuration-cache-problems=warn", "--stacktrace", "--info").build();
    }

    private GradleRunner gradleRunner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withEnvironment(isolatedEnvironment())
                .withArguments(arguments);
    }

    /**
     * Returns an environment that isolates the Gradle build from the host machine:
     * <ul>
     *   <li>{@code ORG_GRADLE_PROJECT_*} variables are removed — Gradle promotes these
     *       to project properties, so any with {@code op://} values would trigger the plugin
     *       for references outside the test fixture.</li>
     *   <li>{@code GRADLE_USER_HOME} is pointed at a temp directory so that
     *       {@code ~/.gradle/gradle.properties} and {@code ~/.gradle/init.d/} are ignored.</li>
     * </ul>
     */
    private Map<String, String> isolatedEnvironment() {
        Map<String, String> env = System.getenv().entrySet().stream()
                .filter(e -> !e.getKey().startsWith("ORG_GRADLE_PROJECT_"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        env.put("GRADLE_USER_HOME", projectDir.resolve("gradle-home").toString());
        return env;
    }

    private Path createOpMock(String behavior) throws IOException {
        Path script = projectDir.resolve("op-mock.sh");
        Files.writeString(
                script,
                "#!/usr/bin/env bash\n" +
                        "set -euo pipefail\n" +
                        behavior + "\n"
        );
        Files.setPosixFilePermissions(
                script,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                )
        );
        return script;
    }

    private Path createStatefulOpMock(Path secretFile, Path invocationCountFile) throws IOException {
        Path script = projectDir.resolve("op-mock.sh");
        Files.writeString(
                script,
                "#!/usr/bin/env bash\n" +
                        "set -euo pipefail\n" +
                        "counter_file=\"" + invocationCountFile + "\"\n" +
                        "if [ -f \"$counter_file\" ]; then\n" +
                        "  count=$(cat \"$counter_file\")\n" +
                        "else\n" +
                        "  count=0\n" +
                        "fi\n" +
                        "count=$((count + 1))\n" +
                        "printf '%s' \"$count\" > \"$counter_file\"\n" +
                        "cat \"" + secretFile + "\"\n"
        );
        Files.setPosixFilePermissions(
                script,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                )
        );
        return script;
    }

    private int readInvocationCount(Path invocationCountFile) throws IOException {
        if (!Files.exists(invocationCountFile)) {
            return 0;
        }
        String rawCount = Files.readString(invocationCountFile).trim();
        return rawCount.isEmpty() ? 0 : Integer.parseInt(rawCount);
    }

    private void assertOutputContains(BuildResult result, String expectedSubstring, String context) {
        String output = result.getOutput();
        if (!output.contains(expectedSubstring)) {
            throw new AssertionFailedError(
                    formatMessage(context, expectedSubstring, output),
                    expectedSubstring,
                    output
            );
        }
    }

    private void assertOutputDoesNotMatchPattern(BuildResult result, String regex, String context) {
        String output = result.getOutput();
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        if (pattern.matcher(output).find()) {
            String expected = "output not matching /" + regex + "/i";
            String actual = extractMatchingLinesWithContext(output, pattern, 3);
            throw new AssertionFailedError(
                    formatMessage(context, expected, actual),
                    expected,
                    actual
            );
        }
    }

    private void assertMessageContains(String message, String expectedSubstring, String context) {
        if (message == null || !message.contains(expectedSubstring)) {
            String actual = String.valueOf(message);
            throw new AssertionFailedError(
                    formatMessage(context, expectedSubstring, actual),
                    expectedSubstring,
                    actual
            );
        }
    }

    private static String formatMessage(String context, String expected, String actual) {
        if ("true".equals(System.getProperty("test.ide"))) {
            return context;
        }
        return context + "\nExpected: " + expected + "\nActual: " + actual;
    }

    private static String extractMatchingLinesWithContext(String text, Pattern pattern, int contextLines) {
        String[] lines = text.split("\n", -1);
        boolean[] include = new boolean[lines.length];
        boolean anyMatch = false;
        for (int i = 0; i < lines.length; i++) {
            if (pattern.matcher(lines[i]).find()) {
                anyMatch = true;
                for (int j = Math.max(0, i - contextLines); j <= Math.min(lines.length - 1, i + contextLines); j++) {
                    include[j] = true;
                }
            }
        }
        if (!anyMatch) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        boolean skipped = false;
        for (int i = 0; i < lines.length; i++) {
            if (include[i]) {
                if (skipped) {
                    sb.append("...\n");
                }
                sb.append(lines[i]).append("\n");
                skipped = false;
            } else {
                skipped = true;
            }
        }
        return sb.toString();
    }

    private void assumePosix() {
        assumeTrue(
                !System.getProperty("os.name").toLowerCase().contains("win"),
                "POSIX scripts are required for this test."
        );
    }
}
