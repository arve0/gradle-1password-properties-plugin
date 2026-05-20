#!/usr/bin/env sh

Describe 'plain string project property behavior'
  BeforeEach 'setup_fixture'
  AfterEach 'cleanup_fixture'

  create_op_mock() {
    OP_MOCK="$TMP_DIR/op-mock.sh"
    cat > "$OP_MOCK" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "functional-secret"
EOF
    chmod +x "$OP_MOCK"
  }

  BeforeEach 'create_op_mock'

  write_build_using_plugin_directly() {
    if [ -n "$LOCAL_MAVEN_REPO" ]; then
      plugin_decl='id("io.github.arve0.1password.properties") version "dev-SNAPSHOT"'
    else
      plugin_decl='id("io.github.arve0.1password.properties")'
    fi
    cat > "$FIXTURE_DIR/build.gradle.kts" <<EOF
plugins {
    $plugin_decl
}

val MY_PROP = project.property("MY_PROP")

tasks.register("printProp") {
    doLast {
        println("MY_PROP=\$MY_PROP")
    }
}
EOF
  }

  BeforeEach 'write_build_using_plugin_directly'

  It 'does not crash when property is a regular string'
    write_gradle_properties "MY_PROP=hello-world"

    When run run_gradle printProp
    The status should be success
    The output should include "MY_PROP=hello-world"
  End

  It 'resolves plain string verbatim when value has no op:// prefix'
    write_gradle_properties "MY_PROP=just-a-string"

    When run run_gradle printProp
    The status should be success
    The output should include "MY_PROP=just-a-string"
  End

  It 'resolves property provided via -P as a plain string'
    write_gradle_properties "onePassword.op.command=$OP_MOCK"

    When run run_gradle printProp -PMY_PROP=cli-value
    The status should be success
    The output should include "MY_PROP=cli-value"
    The output should not include "cannot be cast to class org.gradle.api.provider.Provider"
  End
End
