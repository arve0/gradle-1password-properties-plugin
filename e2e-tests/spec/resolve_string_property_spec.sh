#!/usr/bin/env sh

Describe 'plain string project property behavior'
  BeforeEach 'setup_fixture'
  AfterEach 'cleanup_fixture'

  BeforeEach 'create_op_mock'
  BeforeEach 'prepare_fixture plain_string_property'

  It 'does not crash when property is a regular string'
    write_gradle_properties "MY_PROP=hello-world"

    When run run_gradle printProp
    The status should be success
    The output should include "MY_PROP=hello-world"
  End

  It 'onePassword.property() returns the same value as project.property() for plain strings'
    write_gradle_properties "MY_PROP=hello-world"

    When run run_gradle printProp1Password
    The status should be success
    The output should include "MY_PROP=hello-world"
  End

  It 'resolves property provided via -P as a plain string'
    write_gradle_properties ""

    When run run_gradle printProp -PMY_PROP=cli-value
    The status should be success
    The output should include "MY_PROP=cli-value"
    The output should not include "cannot be cast to class org.gradle.api.provider.Provider"
  End

  It 'project.property() and onePassword.property() return the same value'
    write_gradle_properties "MY_PROP=hello-world"

    When run run_gradle compareProp
    The status should be success
    The output should include "MY_PROP is the same with project.property and onePassword.property"
  End
End
