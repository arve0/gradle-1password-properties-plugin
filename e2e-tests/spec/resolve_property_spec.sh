#!/usr/bin/env sh

Describe 'resolves project property from 1Password reference'
  BeforeEach 'setup_fixture'
  AfterEach 'cleanup_fixture'

  create_op_mock() {
    OP_MOCK="$TMP_DIR/op-mock.sh"
    cat > "$OP_MOCK" <<EOF
#!/usr/bin/env bash
set -euo pipefail
echo "functional-secret"
EOF
    chmod +x "$OP_MOCK"
  }

  BeforeEach 'create_op_mock'

  It 'resolves op:// property value by calling op and printing the result'
    write_gradle_properties "TOKEN=op://vault/item/field"

    When run run_gradle printToken
    The status should be success
    The output should include "TOKEN=functional-secret"
  End
End
