package io.github.arve0.onepassword.properties;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

final class OpCliClient implements SecretReferenceReader {
    private static final Logger LOGGER = Logging.getLogger(OpCliClient.class);
    private static final String DEFAULT_COMMAND = "op";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final String command;
    private final Duration timeout;

    OpCliClient() {
        this(DEFAULT_COMMAND, DEFAULT_TIMEOUT);
    }

    OpCliClient(String command, Duration timeout) {
        this.command = command;
        this.timeout = timeout;
    }

    String getCommand() {
        return command;
    }

    long getTimeoutMillis() {
        return timeout.toMillis();
    }

    static OpCliClient fromProject(Project project) {
        String command = stringProperty(project, "onePassword.op.command", DEFAULT_COMMAND);
        Duration timeout = timeoutProperty(project, "onePassword.op.timeoutMillis", DEFAULT_TIMEOUT);
        return new OpCliClient(command, timeout);
    }

    @Override
    public String read(String reference) {
        Process process = start(reference);
        waitFor(reference, process);
        String stdout = readStream(process, true).trim();
        String stderr = readStream(process, false).trim();
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String details = stderr.isBlank() ? "" : "; stderr: " + stderr;
            throw new OpCliException("1Password CLI exited with code " + exitCode + details + ".");
        }
        if (stdout.isBlank()) {
            throw new OpCliException("1Password CLI returned empty output for reference '" + reference + "'.");
        }
        return stdout;
    }

    private Process start(String reference) {
        ProcessBuilder processBuilder = new ProcessBuilder(command, "read", reference);
        try {
            return processBuilder.start();
        } catch (IOException e) {
            throw new OpCliException(
                    "Unable to execute 1Password CLI command '" + command + "'. Ensure the CLI is installed and on PATH.",
                    e
            );
        }
    }

    private void waitFor(String reference, Process process) {
        LOGGER.info("Waiting for 1Password CLI process (pid={}, timeout={}ms) to resolve '{}'.",
                process.pid(), timeout.toMillis(), reference);
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            LOGGER.info("1Password CLI process (pid={}) finished: {}", process.pid(), finished);
            if (!finished) {
                process.destroyForcibly();
                throw new OpCliException(
                        "1Password CLI timed out after " + timeout.toMillis() + "ms for reference '" + reference + "'."
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpCliException("Interrupted while waiting for 1Password CLI result.", e);
        }
    }

    private String readStream(Process process, boolean standardOutput) {
        try {
            byte[] bytes = standardOutput
                    ? process.getInputStream().readAllBytes()
                    : process.getErrorStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OpCliException("Failed to read output from 1Password CLI.", e);
        }
    }

    private static String stringProperty(Project project, String propertyName, String fallback) {
        Object value = project.findProperty(propertyName);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        throw new IllegalArgumentException("Property '" + propertyName + "' must be a non-empty String.");
    }

    private static Duration timeoutProperty(Project project, String propertyName, Duration fallback) {
        Object value = project.findProperty(propertyName);
        if (value == null) {
            return fallback;
        }
        long timeoutMillis;
        if (value instanceof Number numberValue) {
            timeoutMillis = numberValue.longValue();
        } else if (value instanceof String stringValue) {
            try {
                timeoutMillis = Long.parseLong(stringValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Property '" + propertyName + "' must be a whole number of milliseconds.",
                        e
                );
            }
        } else {
            throw new IllegalArgumentException("Property '" + propertyName + "' must be numeric.");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Property '" + propertyName + "' must be greater than zero.");
        }
        return Duration.ofMillis(timeoutMillis);
    }
}
