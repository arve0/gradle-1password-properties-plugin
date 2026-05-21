#!/usr/bin/env sh

Describe 'uses 1Password plugin from buildSrc convention plugin'
  BeforeEach 'setup_fixture'
  AfterEach 'cleanup_fixture'

  BeforeEach 'create_op_mock'
  BeforeEach 'prepare_fixture buildsrc_convention'

  It 'resolves TOKEN when plugin is applied in buildSrc convention plugin'
    write_gradle_properties "TOKEN=op://vault/item/field"

    When run run_gradle printTokenFromConvention
    The status should be success
    The output should include "TOKEN=functional-secret"
  End
End