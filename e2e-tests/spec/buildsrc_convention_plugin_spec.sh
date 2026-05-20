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

  write_buildsrc_convention_fixture() {
    mkdir -p "$FIXTURE_DIR/buildSrc/src/main/kotlin"

    if [ -n "$LOCAL_MAVEN_REPO" ]; then
      cat > "$FIXTURE_DIR/settings.gradle.kts" <<EOF
pluginManagement {
    repositories {
        maven { url = uri("$LOCAL_MAVEN_REPO") }
        gradlePluginPortal()
    }
}
rootProject.name = "functional-test"
EOF
      cat > "$FIXTURE_DIR/buildSrc/settings.gradle.kts" <<EOF
pluginManagement {
    repositories {
        maven { url = uri("$LOCAL_MAVEN_REPO") }
        gradlePluginPortal()
    }
}
EOF
      cat > "$FIXTURE_DIR/buildSrc/src/main/kotlin/my.convention.gradle.kts" <<'EOF'
plugins {
    id("io.github.arve0.1password.properties")
}

tasks.register("printTokenFromConvention") {
    val token = project.property("TOKEN") as org.gradle.api.provider.Provider<*>
    doLast {
        println("TOKEN=${token.get()}")
    }
}
EOF
    else
      cat > "$FIXTURE_DIR/settings.gradle.kts" <<EOF
pluginManagement {
    includeBuild("$PROJECT_ROOT")
}
rootProject.name = "functional-test"
EOF
      cat > "$FIXTURE_DIR/buildSrc/settings.gradle.kts" <<EOF
pluginManagement {
    includeBuild("$PROJECT_ROOT")
}
EOF
      cat > "$FIXTURE_DIR/buildSrc/src/main/kotlin/my.convention.gradle.kts" <<'EOF'
plugins {
    id("io.github.arve0.1password.properties")
}

tasks.register("printTokenFromConvention") {
    val token = project.property("TOKEN") as org.gradle.api.provider.Provider<*>
    doLast {
        println("TOKEN=${token.get()}")
    }
}
EOF
    fi

    if [ -n "$LOCAL_MAVEN_REPO" ]; then
      cat > "$FIXTURE_DIR/buildSrc/build.gradle.kts" <<EOF
plugins {
    \`kotlin-dsl\`
}

repositories {
    maven { url = uri("$LOCAL_MAVEN_REPO") }
    gradlePluginPortal()
}

dependencies {
    implementation("io.github.arve0.1password.properties:io.github.arve0.1password.properties.gradle.plugin:dev-SNAPSHOT")
}
EOF
    else
      cat > "$FIXTURE_DIR/buildSrc/build.gradle.kts" <<'EOF'
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}
EOF
    fi

    cat > "$FIXTURE_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("my.convention")
}
EOF
  }

  BeforeEach 'create_op_mock'
  BeforeEach 'write_buildsrc_convention_fixture'

  It 'resolves TOKEN when plugin is applied in buildSrc convention plugin'
    write_gradle_properties "TOKEN=op://vault/item/field"

    When run run_gradle printTokenFromConvention
    The status should be success
    The output should include "TOKEN=functional-secret"
  End
End