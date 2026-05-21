#!/usr/bin/env sh

Describe 'uses 1Password plugin from buildSrc convention plugin'
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
  BeforeEach 'prepare_fixture buildsrc_convention'

  It 'resolves TOKEN when plugin is applied in buildSrc convention plugin'
    write_gradle_properties "TOKEN=op://vault/item/field"

    When run run_gradle printTokenFromConvention
    The status should be success
    The output should include "TOKEN=functional-secret"
  End
End