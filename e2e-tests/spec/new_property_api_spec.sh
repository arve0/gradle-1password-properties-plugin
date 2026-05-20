#!/usr/bin/env sh

Describe 'onePassword.property() API'
  BeforeEach 'setup_fixture'
  AfterEach 'cleanup_fixture'

  create_op_mock() {
    OP_MOCK="$TMP_DIR/op-mock.sh"
    cat > "$OP_MOCK" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "mocked-secret"
EOF
    chmod +x "$OP_MOCK"
  }

  BeforeEach 'create_op_mock'
  BeforeEach 'prepare_fixture api_new_property'

  It 'resolves plain property from gradle.properties'
    write_gradle_properties "TOKEN=plain-value"

    When run run_gradle printToken
    The status should be success
    The output should include "TOKEN=plain-value"
  End

  It 'resolves op:// property from gradle.properties'
    write_gradle_properties "TOKEN=op://vault/item/field"

    When run run_gradle printToken
    The status should be success
    The output should include "TOKEN=mocked-secret"
  End

  It 'resolves plain property from -P flag'
    write_gradle_properties ""

    When run run_gradle printToken -PTOKEN=from-cli
    The status should be success
    The output should include "TOKEN=from-cli"
    The output should not include "cannot be cast to class"
  End

  It 'resolves plain property from ORG_GRADLE_PROJECT_ environment variable'
    write_gradle_properties ""
    run_gradle_with_env() {
      ORG_GRADLE_PROJECT_TOKEN=from-env run_gradle printToken
    }

    When run run_gradle_with_env
    The status should be success
    The output should include "TOKEN=from-env"
  End

  It 'respects Gradle precedence: -P overrides gradle.properties'
    write_gradle_properties "TOKEN=from-gradle-properties"

    When run run_gradle printToken -PTOKEN=from-cli
    The status should be success
    The output should include "TOKEN=from-cli"
  End
End
