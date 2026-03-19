package io.github.arve0.onepassword.properties;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        BuildResult secondRun = runBuildWithConfigurationCache("printToken");

        assertOutputContains(firstRun, "TOKEN=functional-secret", "first run should resolve token");
        assertEquals(1, readInvocationCount(invocationCountFile));

        assertOutputContains(secondRun, "TOKEN=functional-secret", "second run should still print the same token");
        assertOutputContains(secondRun, "Configuration cache entry reused", "second run should reuse configuration cache");
        assertEquals(1, readInvocationCount(invocationCountFile));
    }

    @Test
    void doNotInvalidateConfigurationCacheWhenSecretChanges() throws IOException {
        assumePosix();
        Path secretFile = projectDir.resolve("secret.txt");
        Path invocationCountFile = projectDir.resolve("op-invocations.txt");
        Files.writeString(secretFile, "functional-secret\n");
        Path opMock = createStatefulOpMock(secretFile, invocationCountFile);
        writeProjectFiles(opMock, "TOKEN=op://vault/item/field");

        BuildResult firstRun = runBuildWithConfigurationCache("printToken");
        assertOutputContains(firstRun, "TOKEN=functional-secret", "first run should resolve initial token");
        assertEquals(1, readInvocationCount(invocationCountFile));

        Files.writeString(secretFile, "changed-secret\n");

        BuildResult secondRun = runBuildWithConfigurationCache("printToken");

        assertOutputContains(secondRun, "TOKEN=functional-secret", "second run should use cached token even after secret change");
        assertOutputContains(secondRun, "Configuration cache entry reused", "second run should reuse cached configuration");
        assertEquals(1, readInvocationCount(invocationCountFile));
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
        assertOutputContains(firstRun, "TOKEN=functional-secret", "first run should resolve token");
        assertEquals(1, readInvocationCount(invocationCountFile));

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

        assertOutputContains(secondRun, "TOKEN=functional-secret", "second run should use cached token");
        assertOutputContains(secondRun, "Configuration cache entry reused", "second run should reuse configuration cache");
        assertEquals(1, readInvocationCount(invocationCountFile));
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
        assertOutputContains(firstRun, "TOKEN=functional-secret", "first run should resolve initial token");
        assertEquals(1, readInvocationCount(invocationCountFile));

        Files.writeString(secretFile, "changed-secret\n");

        BuildResult secondRun = runBuild("printToken");

        assertOutputContains(secondRun, "TOKEN=changed-secret", "second run should resolve changed secret without configuration cache");
        assertEquals(2, readInvocationCount(invocationCountFile));
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
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(taskName, "--stacktrace")
                .build();
    }

    private BuildResult runBuildWithConfigurationCache(String taskName) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(taskName, "--configuration-cache", "--configuration-cache-problems=warn", "--stacktrace", "--info")
                .build();
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
        assertTrue(
                output.contains(expectedSubstring),
                () -> "Expected build output to contain '" + expectedSubstring + "' (" + context + "), but it did not.\n"
                        + "--- build output ---\n"
                        + output
        );
    }

    private void assertMessageContains(String message, String expectedSubstring, String context) {
        assertTrue(
                message != null && message.contains(expectedSubstring),
                () -> "Expected message to contain '" + expectedSubstring + "' (" + context + "), but it did not.\n"
                        + "--- actual message ---\n"
                        + String.valueOf(message)
        );
    }

    private void assumePosix() {
        assumeTrue(
                !System.getProperty("os.name").toLowerCase().contains("win"),
                "POSIX scripts are required for this test."
        );
    }
}
