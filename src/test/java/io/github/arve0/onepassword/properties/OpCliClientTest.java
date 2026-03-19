package io.github.arve0.onepassword.properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpCliClientTest {

    @TempDir
    Path tempDir;

    @Test
    void readsSecretOnSuccess() throws IOException {
        assumePosix();
        Path script = createExecutable("echo \"secret-value\"");
        OpCliClient client = new OpCliClient(script.toString(), Duration.ofSeconds(2));

        String value = client.read("op://vault/item/field");

        assertEquals("secret-value", value);
    }

    @Test
    void failsWhenCliIsMissing() {
        OpCliClient client = new OpCliClient("op-command-that-does-not-exist", Duration.ofMillis(500));

        OpCliException exception = assertThrows(OpCliException.class, () -> client.read("op://vault/item/field"));

        assertTrue(exception.getMessage().contains("Unable to execute 1Password CLI command"));
    }

    @Test
    void failsOnTimeout() throws IOException {
        assumePosix();
        Path script = createExecutable("sleep 2\necho \"late\"");
        OpCliClient client = new OpCliClient(script.toString(), Duration.ofMillis(100));

        OpCliException exception = assertThrows(OpCliException.class, () -> client.read("op://vault/item/field"));

        assertTrue(exception.getMessage().contains("timed out"));
    }

    @Test
    void failsOnNonZeroExitCode() throws IOException {
        assumePosix();
        Path script = createExecutable("echo \"boom\" >&2\nexit 13");
        OpCliClient client = new OpCliClient(script.toString(), Duration.ofSeconds(2));

        OpCliException exception = assertThrows(OpCliException.class, () -> client.read("op://vault/item/field"));

        assertTrue(exception.getMessage().contains("exited with code 13"));
        assertTrue(exception.getMessage().contains("boom"));
    }

    @Test
    void failsWhenOutputIsEmpty() throws IOException {
        assumePosix();
        Path script = createExecutable("echo \"   \"");
        OpCliClient client = new OpCliClient(script.toString(), Duration.ofSeconds(2));

        OpCliException exception = assertThrows(OpCliException.class, () -> client.read("op://vault/item/field"));

        assertTrue(exception.getMessage().contains("returned empty output"));
    }

    private Path createExecutable(String body) throws IOException {
        Path script = tempDir.resolve("op-mock.sh");
        Files.writeString(
                script,
                "#!/usr/bin/env bash\n" +
                        "set -euo pipefail\n" +
                        body + "\n"
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

    private void assumePosix() {
        assumeTrue(
                !System.getProperty("os.name").toLowerCase().contains("win"),
                "POSIX scripts are required for this test."
        );
    }
}
