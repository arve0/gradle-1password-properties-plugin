#!/usr/bin/env sh

Describe 'surfaces invalid 1Password reference'
  BeforeEach 'setup_fixture'
  AfterEach 'cleanup_fixture'

  create_op_mock() {
    OP_MOCK="$TMP_DIR/op-mock.sh"
    cat > "$OP_MOCK" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "ignored"
EOF
    chmod +x "$OP_MOCK"
  }

  BeforeEach 'create_op_mock'

  It 'fails the build and reports property name and validation reason'
    write_gradle_properties "TOKEN=op://"

    When run run_gradle printToken
    The status should be failure
    The output should include "Property 'TOKEN'"
    The output should include "invalid 1Password reference"
  End
End
