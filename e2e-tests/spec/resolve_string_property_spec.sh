#!/usr/bin/env sh

Describe 'resolveProjectPropertyToString helper function'
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

  write_helper_build() {
    if [ -n "$LOCAL_MAVEN_REPO" ]; then
      plugin_decl='id("io.github.arve0.1password.properties") version "dev-SNAPSHOT"'
    else
      plugin_decl='id("io.github.arve0.1password.properties")'
    fi
    cat > "$FIXTURE_DIR/build.gradle.kts" <<EOF
import org.gradle.api.provider.Provider

plugins {
    $plugin_decl
}

fun resolveProjectPropertyToString(name: String): String {
    val value = project.property(name)
    return if (value is Provider<*>) value.get().toString() else value.toString()
}

val MY_PROP = resolveProjectPropertyToString("MY_PROP")

tasks.register("printProp") {
    doLast {
        println("MY_PROP=\$MY_PROP")
    }
}
EOF
  }

  BeforeEach 'write_helper_build'

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
End
