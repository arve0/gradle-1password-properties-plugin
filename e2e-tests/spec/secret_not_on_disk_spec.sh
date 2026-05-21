#!/usr/bin/env sh

Describe 'secret is not left easily searchable on disk'
  BeforeEach 'setup_fixture'
  BeforeEach 'use_isolated_gradle_home'
  BeforeEach 'prepare_fixture secret_not_on_disk'
  AfterEach 'cleanup_fixture'

  SECRET="really-secret-not-likely-to-happen-by-coincidence"

  # Runs a full build with configuration cache, stops the daemon so its logs
  # are flushed, then scans .gradle and gradle-home for the secret string.
  run_build_then_scan() {
    OP_MOCK_OUTPUT="$SECRET" create_op_mock
    write_gradle_properties "TOKEN=op://vault/item/field"

    status="$(run_gradle_capture useToken --configuration-cache --configuration-cache-problems=warn --info)"
    [ "$status" -eq 0 ] || { cat "$LAST_OUTPUT_FILE"; return 1; }

    # Verify the correct secret was actually used.
    used_token="$(cat "$FIXTURE_DIR/build/used-token.txt" 2>/dev/null || echo "")"
    [ "$used_token" = "$SECRET" ] || { echo "token mismatch: '$used_token'"; return 1; }

    stop_gradle_daemon

    scan_output="$(assert_no_secret_on_disk "$SECRET")" || return 1
    printf '%s\n' "$scan_output"
  }

  It 'the build succeeds and the secret cannot be found in gradle directories'
    When run run_build_then_scan
    The status should be success
    The output should include "secret not found"
  End
End
