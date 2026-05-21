#!/usr/bin/env sh

Describe 'timeout when 1Password CLI takes too long'
  BeforeEach 'setup_fixture'
  AfterEach 'cleanup_fixture'

  BeforeEach 'create_sleeping_op_mock'

  create_sleeping_op_mock() {
    OP_MOCK="$TMP_DIR/op-mock.sh"
    export OP_MOCK
    cp "$SHELLSPEC_PROJECT_ROOT/fixtures/op-mock-sleep.sh" "$OP_MOCK"
    chmod +x "$OP_MOCK"
  }

  It 'fails the build with a timeout error when op exceeds timeoutMillis'
    write_gradle_properties "TOKEN=op://vault/item/field
onePassword.op.timeoutMillis=500"

    When run run_gradle printToken
    The status should be failure
    The output should include "1Password CLI timed out"
  End
End
