#!/usr/bin/env sh

Describe 'surfaces invalid 1Password reference'
  BeforeEach 'setup_fixture'
  AfterEach 'cleanup_fixture'

  BeforeEach 'create_op_mock'

  It 'fails the build and reports property name and validation reason'
    write_gradle_properties "TOKEN=op://"

    When run run_gradle printToken
    The status should be failure
    The output should include "Property 'TOKEN'"
    The output should include "invalid 1Password reference"
  End
End
