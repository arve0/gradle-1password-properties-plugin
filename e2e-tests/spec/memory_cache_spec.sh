#!/usr/bin/env sh

Describe 'memory cache behaviour'
  BeforeEach 'setup_fixture'
  BeforeEach 'create_stateful_op_mock'
  BeforeEach 'prepare_fixture memory_cache'
  BeforeEach 'create_fifty_subprojects'
  AfterEach 'cleanup_fixture'

  create_fifty_subprojects() {
    template="$FIXTURE_DIR/project-a/build.gradle.kts"
    i=1
    while [ "$i" -le 50 ]; do
      subproject="subproject-$i"
      mkdir -p "$FIXTURE_DIR/$subproject"
      cp "$template" "$FIXTURE_DIR/$subproject/build.gradle.kts"
      i=$((i + 1))
    done
    includes=$(i=1; while [ "$i" -le 50 ]; do
      printf '"subproject-%s"' "$i"
      [ "$i" -lt 50 ] && printf ', '
      i=$((i + 1))
    done)
    awk -v inc="$includes" '/^include\(/{print "include(" inc ")"; next} {print}' \
      "$FIXTURE_DIR/settings.gradle.kts" > "$FIXTURE_DIR/settings.gradle.kts.tmp"
    mv "$FIXTURE_DIR/settings.gradle.kts.tmp" "$FIXTURE_DIR/settings.gradle.kts"
  }

  read_invocations() {
    if [ -f "$INVOCATION_COUNT_FILE" ]; then cat "$INVOCATION_COUNT_FILE"; else echo 0; fi
  }

  It 'calls op cli only once when token.get() is called in configuration phase for fifty projects'
    write_gradle_properties "TOKEN=op://vault/item/field"

    run_gradle_capture printToken >/dev/null
    invocations="$(read_invocations)"
    token_prints="$(grep -c 'TOKEN=' "$LAST_OUTPUT_FILE" || echo 0)"

    When run printf '%s\n%s\n' "$invocations" "$token_prints"
    The line 1 of output should equal "1"
    The line 2 of output should equal "100"
  End
End
