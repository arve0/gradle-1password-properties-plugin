#!/usr/bin/env sh

Describe 'secret is not left easily searchable on disk'
  BeforeEach 'setup_fixture'
  BeforeEach 'use_isolated_gradle_home'
  AfterEach 'cleanup_fixture'

  create_op_mock() {
    OP_MOCK="$TMP_DIR/op-mock.sh"
    cat > "$OP_MOCK" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "really-secret-not-likely-to-happen-by-coincidence"
EOF
    chmod +x "$OP_MOCK"
  }

  # Write a build file that uses the token at execution time only.
  # The secret is resolved in doLast and written to a file — it never
  # appears on stdout, so it does not end up in Gradle daemon logs.
  write_execution_time_build_file() {
    cat > "$FIXTURE_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("io.github.arve0.1password.properties") version "dev-SNAPSHOT"
}

tasks.register("useToken") {
    val token = project.property("TOKEN") as org.gradle.api.provider.Provider<*>
    val outputFile = layout.buildDirectory.file("used-token.txt")
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.also { it.parentFile.mkdirs() }.writeText(token.get().toString())
    }
}
EOF
  }

  # Runs a full build with configuration cache, stops the daemon so its logs
  # are flushed, then scans .gradle and gradle-home for the secret string.
  run_build_then_scan() {
    create_op_mock
    write_gradle_properties "TOKEN=op://vault/item/field"
    write_execution_time_build_file

    status="$(run_gradle_capture useToken --configuration-cache --configuration-cache-problems=warn --info)"
    [ "$status" -eq 0 ] || { cat "$LAST_OUTPUT_FILE"; return 1; }

    # Verify the correct secret was actually used.
    used_token="$(cat "$FIXTURE_DIR/build/used-token.txt" 2>/dev/null || echo "")"
    [ "$used_token" = "really-secret-not-likely-to-happen-by-coincidence" ] || { echo "token mismatch: '$used_token'"; return 1; }

    stop_gradle_daemon

    scan_output="$(assert_no_secret_on_disk "really-secret-not-likely-to-happen-by-coincidence")" || return 1
    printf '%s\n' "$scan_output"
  }

  It 'the build succeeds and the secret cannot be found in gradle directories'
    When run run_build_then_scan
    The status should be success
    The output should include "secret not found"
  End
End
