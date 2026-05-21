#!/usr/bin/env sh

Describe 'memory cache behaviour'
  BeforeEach 'setup_fixture'
  BeforeEach 'create_stateful_op_mock'
  BeforeEach 'prepare_fixture memory_cache'
  AfterEach 'cleanup_fixture'

  read_invocations() {
    if [ -f "$INVOCATION_COUNT_FILE" ]; then cat "$INVOCATION_COUNT_FILE"; else echo 0; fi
  }

  It 'calls op cli only once when token.get() is called in configuration phase for two projects'
    write_gradle_properties "TOKEN=op://vault/item/field"

    run_gradle_capture printToken >/dev/null
    invocations="$(read_invocations)"

    When run echo "$invocations"
    The output should equal "1"
  End
End
