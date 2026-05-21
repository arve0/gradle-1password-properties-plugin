#!/usr/bin/env sh

Describe 'resolves project property from 1Password reference'
  BeforeEach 'setup_fixture'
  AfterEach 'cleanup_fixture'

  BeforeEach 'create_op_mock'

  It 'resolves op:// property value by calling op and printing the result'
    write_gradle_properties "TOKEN=op://vault/item/field"

    When run run_gradle printToken
    The status should be success
    The output should include "TOKEN=functional-secret"
  End
End
