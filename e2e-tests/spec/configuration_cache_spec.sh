#!/usr/bin/env sh

Describe 'configuration cache behaviour'
  BeforeEach 'setup_fixture'
  BeforeEach 'create_stateful_op_mock'
  AfterEach 'cleanup_fixture'

  read_invocations() {
    if [ -f "$INVOCATION_COUNT_FILE" ]; then cat "$INVOCATION_COUNT_FILE"; else echo 0; fi
  }

  set_secret_value() {
    printf '%s\n' "$1" > "$SECRET_FILE"
  }

  # Runs gradle with configuration cache; discards the printed exit code.
  run_with_cache() {
    run_gradle_capture printToken --configuration-cache --configuration-cache-problems=warn --info >/dev/null
  }

  It 'reuses the cache on second build and calls op on every build'
    write_gradle_properties "TOKEN=op://vault/item/field"

    run_with_cache
    first_output="$(cat "$LAST_OUTPUT_FILE")"
    count_after_first="$(read_invocations)"
    [ "$count_after_first" = "1" ] || exit 1

    run_with_cache
    second_output="$(cat "$LAST_OUTPUT_FILE")"
    count_after_second="$(read_invocations)"
    [ "$count_after_second" = "2" ] || exit 1

    When run echo "$first_output"$'\n'"$second_output"
    The output should include "TOKEN=functional-secret"
    The output should include "Configuration cache entry reused"
  End

  It 'reads the changed secret on second build when cache is reused'
    write_gradle_properties "TOKEN=op://vault/item/field"

    run_with_cache
    count_after_first="$(read_invocations)"
    [ "$count_after_first" = "1" ] || exit 1
    set_secret_value "changed-secret"

    run_with_cache
    second_output="$(cat "$LAST_OUTPUT_FILE")"
    count_after_second="$(read_invocations)"
    [ "$count_after_second" = "2" ] || exit 1

    When run echo "$second_output"
    The status should be success
    The output should include "TOKEN=changed-secret"
    The output should include "Configuration cache entry reused"
  End

  It 'calls op on every build even when configuration cache is warm'
    write_gradle_properties "TOKEN=op://vault/item/field"

    run_with_cache
    count_after_first="$(read_invocations)"
    [ "$count_after_first" = "1" ] || exit 1

    run_with_cache
    second_output="$(cat "$LAST_OUTPUT_FILE")"
    count_after_second="$(read_invocations)"
    [ "$count_after_second" = "2" ] || exit 1

    When run echo "$second_output"
    The status should be success
    The output should include "TOKEN=functional-secret"
    The output should include "Configuration cache entry reused"
  End

  It 'reads the changed secret when --no-configuration-cache bypasses the cache'
    write_gradle_properties "TOKEN=op://vault/item/field"

    run_gradle_capture printToken >/dev/null
    count_after_first="$(read_invocations)"
    [ "$count_after_first" = "1" ] || exit 1
    set_secret_value "changed-secret"

    run_gradle_capture printToken >/dev/null
    second_output="$(cat "$LAST_OUTPUT_FILE")"
    count_after_second="$(read_invocations)"

    [ "$count_after_second" -eq $((count_after_first + 1)) ] || exit 1

    When run echo "$second_output"
    The status should be success
    The output should include "TOKEN=changed-secret"
  End

  It 'stores the configuration cache without any cache problems'
    write_gradle_properties "TOKEN=op://vault/item/field"

    When run run_gradle printToken --configuration-cache --configuration-cache-problems=warn --info
    The status should be success
    The output should include "TOKEN=functional-secret"
    The output should not include "problem with the configuration cache"
  End
End
