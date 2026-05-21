#!/usr/bin/env sh

PROJECT_ROOT="$(cd "$SHELLSPEC_PROJECT_ROOT/.." && pwd)"

# SHARED_GRADLE_HOME and LOCAL_MAVEN_REPO may be set by runner scripts to share
# a single Gradle daemon and pre-built plugin across all parallel test workers.
# If unset, each test falls back to an isolated per-fixture gradle home and
# uses includeBuild to reference the plugin source directly.
: "${SHARED_GRADLE_HOME:=}"
: "${LOCAL_MAVEN_REPO:=}"

TMP_DIR=
FIXTURE_DIR=
FIXTURE_GRADLE_HOME=
OP_MOCK=
OP_MOCK_OUTPUT=
LAST_OUTPUT_FILE=

# Call in a BeforeEach after setup_fixture to give this test its own Gradle
# daemon. Required when the test calls stop_gradle_daemon or scans gradle-home
# for secrets, to avoid interfering with parallel tests sharing SHARED_GRADLE_HOME.
use_isolated_gradle_home() {
  FIXTURE_GRADLE_HOME="$FIXTURE_DIR/gradle-home"
}

setup_fixture() {
  TMP_DIR="$(mktemp -d)"
  FIXTURE_DIR="$TMP_DIR/fixture"
  FIXTURE_GRADLE_HOME="${SHARED_GRADLE_HOME:-$FIXTURE_DIR/gradle-home}"
  mkdir -p "$FIXTURE_DIR"
  LAST_OUTPUT_FILE="$TMP_DIR/last-output.log"
  prepare_fixture default
}

cleanup_fixture() {
  if [ -n "${TMP_DIR:-}" ] && [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
  fi
}

write_gradle_properties() {
  EXTRA_PROPERTIES="$1" \
    render_fixture_file "$SHELLSPEC_PROJECT_ROOT/fixtures/gradle.properties" \
      "$FIXTURE_DIR/gradle.properties" \
      '${EXTRA_PROPERTIES} ${OP_MOCK}'
}

run_gradle_capture() {
  status_file="$TMP_DIR/status"
  (
    cd "$FIXTURE_DIR" || exit 1
    GRADLE_USER_HOME="$FIXTURE_GRADLE_HOME" \
      "$PROJECT_ROOT/gradlew" --stacktrace "$@" >"$LAST_OUTPUT_FILE" 2>&1
    echo "$?" > "$status_file"
  )
  if [ -f "$status_file" ]; then
    cat "$status_file"
  else
    echo 1
  fi
}

run_gradle() {
  status="$(run_gradle_capture "$@")"
  cat "$LAST_OUTPUT_FILE"
  return "$status"
}

stop_gradle_daemon() {
  (
    cd "$FIXTURE_DIR" || exit 1
    GRADLE_USER_HOME="$FIXTURE_GRADLE_HOME" "$PROJECT_ROOT/gradlew" --stop >"$TMP_DIR/stop.log" 2>&1 || true
  )
}

# Renders the shared op-mock fixture and sets OP_MOCK.
# Override OP_MOCK_OUTPUT before calling to change what the mock prints.
# Default output: "functional-secret"
create_op_mock() {
  OP_MOCK="$TMP_DIR/op-mock.sh"
  OP_MOCK_OUTPUT="${OP_MOCK_OUTPUT:-functional-secret}"
  export OP_MOCK OP_MOCK_OUTPUT
  render_fixture_file "$SHELLSPEC_PROJECT_ROOT/fixtures/op-mock.sh" "$OP_MOCK" '${OP_MOCK_OUTPUT}'
  chmod +x "$OP_MOCK"
}

# Creates a stateful op mock that counts invocations and reads the secret from a file.
# Sets: OP_MOCK, SECRET_FILE, INVOCATION_COUNT_FILE
# Write a value to SECRET_FILE to change what the mock returns between builds.
create_stateful_op_mock() {
  SECRET_FILE="$TMP_DIR/secret.txt"
  INVOCATION_COUNT_FILE="$TMP_DIR/op-invocations.txt"
  printf '%s\n' "functional-secret" > "$SECRET_FILE"
  OP_MOCK="$TMP_DIR/op-mock.sh"
  export OP_MOCK SECRET_FILE INVOCATION_COUNT_FILE
  render_fixture_file "$SHELLSPEC_PROJECT_ROOT/fixtures/op-mock-stateful.sh" "$OP_MOCK" \
    '${SECRET_FILE} ${INVOCATION_COUNT_FILE}'
  chmod +x "$OP_MOCK"
}

# Sets environment variables needed for envsubst rendering of fixture templates.
# Call after setup_fixture to set up the substitution context.
setup_fixture_vars() {
  if [ -n "$LOCAL_MAVEN_REPO" ]; then
    PLUGIN_VERSION_DECLARATION='version "dev-SNAPSHOT"'
    PLUGIN_REPO_BLOCK='repositories { maven { url = uri("'"$LOCAL_MAVEN_REPO"'") }; gradlePluginPortal() }'
    BUILDSRC_REPOSITORIES='repositories { maven { url = uri("'"$LOCAL_MAVEN_REPO"'") }; gradlePluginPortal() }'
    BUILDSRC_DEPENDENCIES='dependencies { implementation("io.github.arve0.1password.properties:io.github.arve0.1password.properties.gradle.plugin:dev-SNAPSHOT") }'
  else
    PLUGIN_VERSION_DECLARATION=""
    PLUGIN_REPO_BLOCK='includeBuild("'"$PROJECT_ROOT"'")'
    BUILDSRC_REPOSITORIES='repositories { gradlePluginPortal() }'
    BUILDSRC_DEPENDENCIES=""
  fi
  export PLUGIN_VERSION_DECLARATION PLUGIN_REPO_BLOCK BUILDSRC_REPOSITORIES BUILDSRC_DEPENDENCIES
}

# Render a fixture template file with envsubst and write to the target path.
# Only substitutes the listed variables, leaving Kotlin ${...} syntax intact.
# Usage: render_fixture_file <source_template> <target_path> <VAR_LIST>
# VAR_LIST is a space-separated list of shell variable names to substitute,
# e.g., '${PLUGIN_VERSION_DECLARATION} ${PLUGIN_REPO_BLOCK}'
render_fixture_file() {
  source="$1"
  target="$2"
  vars="$3"
  envsubst "$vars" < "$source" > "$target"
}

# Copy and render all template files from a fixture folder into FIXTURE_DIR.
# Calls setup_fixture_vars if not already called.
# Usage: prepare_fixture <fixture_name>
# fixture_name is a folder under e2e-tests/fixtures/
prepare_fixture() {
  fixture_name="$1"
  fixture_src="$SHELLSPEC_PROJECT_ROOT/fixtures/$fixture_name"
  setup_fixture_vars
  find "$fixture_src" -type f | while IFS= read -r src_file; do
    rel_path="${src_file#"$fixture_src"/}"
    target="$FIXTURE_DIR/$rel_path"
    mkdir -p "$(dirname "$target")"
    render_fixture_file "$src_file" "$target" \
      '${PLUGIN_VERSION_DECLARATION} ${PLUGIN_REPO_BLOCK} ${OP_MOCK} ${BUILDSRC_REPOSITORIES} ${BUILDSRC_DEPENDENCIES}'
  done
}

assert_no_secret_on_disk() {
  secret="$1"
  rg -a --no-ignore --fixed-strings -l "$secret" "$FIXTURE_DIR/.gradle" "$FIXTURE_GRADLE_HOME" >"$TMP_DIR/rg-findings.log" 2>&1 || true
  rg_findings="$(cat "$TMP_DIR/rg-findings.log")"
  fd . "$FIXTURE_DIR/.gradle" --type f -0 2>/dev/null | xargs -0 strings 2>/dev/null | grep -F "$secret" >"$TMP_DIR/fd-findings.log" 2>&1 || true
  fd . "$FIXTURE_GRADLE_HOME" --type f -0 2>/dev/null | xargs -0 strings 2>/dev/null | grep -F "$secret" >>"$TMP_DIR/fd-findings.log" 2>&1 || true
  fd_findings="$(cat "$TMP_DIR/fd-findings.log")"
  if [ -n "$rg_findings" ] || [ -n "$fd_findings" ]; then
    echo "secret found on disk"
    echo "$rg_findings"
    echo "$fd_findings"
    return 1
  fi
  echo "secret not found"
}
