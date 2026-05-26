# gradle-1password-properties-plugin

This plugin resolves Gradle project properties prefixed with `op://` by calling the 1Password CLI (`op read <reference>`). The main goal is to avoid secrets being stored to disk.

[io.github.arve0.1password.properties @ Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.arve0.1password.properties)

## Usage
The plugin tries to be a drop in replacement for `project.property`, but needs an extra `.get()` when accessing the secret. Example:

`build.gradle.kts`:

```kotlin
plugins {
    id("io.github.arve0.1password.properties") version "1.1.1"
}

val githubToken: Provider<String> = onePassword.property("GITHUB_TOKEN")

tasks.register("printToken") {
    doLast {
        // Resolved at execution time — not stored in configuration cache
        println("token: ${githubToken.get().subSequence(0, 7)}")
    }
}
```

Run the task:

```bash
./gradlew printToken
```

### Property sources

`onePassword.property("KEY")` resolves from all standard Gradle property sources with normal precedence:

| Source | Example |
|---|---|
| `gradle.properties` | `GITHUB_TOKEN=op://Personal/Github/token` |
| Environment variable | `ORG_GRADLE_PROJECT_GITHUB_TOKEN=my-plain-token` |
| Command-line flag | `-PGITHUB_TOKEN=my-plain-token` |

Plain string values and `op://` references behave identically from the user's perspective — the plugin resolves `op://` references lazily and returns the value as `Provider<String>` either way.

### Usage from buildSrc convention plugins

When this plugin is used inside a precompiled script plugin in `buildSrc`
(for example `buildSrc/src/main/kotlin/my.convention.gradle.kts`), Gradle has
one important rule:

- Do **not** declare a plugin version in the precompiled script plugin file.
- The plugin must be added as an implementation dependency of `buildSrc`.

Use the plugin marker artifact in `buildSrc/build.gradle.kts` (no
`includeBuild` needed):

```kotlin
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("io.github.arve0.1password.properties:io.github.arve0.1password.properties.gradle.plugin:1.1.1")
}
```

`buildSrc/src/main/kotlin/my.convention.gradle.kts`:

```kotlin
plugins {
    id("io.github.arve0.1password.properties")
}

tasks.register("printTokenFromConvention") {
    val token: Provider<String> = onePassword.property("TOKEN")
    doLast {
        println("token: ${token.get().subSequence(0, 7)}")
    }
}
```

Root `build.gradle.kts`:

```kotlin
plugins {
    id("my.convention")
}
```

Run the task from the convention plugin:

```bash
./gradlew printTokenFromConvention
```

### Configuration cache

This plugin is compatible with the [Gradle configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html).
Properties are exposed as `Provider<String>`, so whether a secret ends up in
the configuration cache depends entirely on **when** your build calls `.get()`:

**Execution time (recommended)** — call `.get()` inside `doLast` or another
task action. Gradle does not store the value in the cache; `op` is called once
per build when the task runs.

```kotlin
tasks.register("deploy") {
    val token: Provider<String> = onePassword.property("DEPLOY_TOKEN")
    doLast {
        // resolved at execution time, not stored in configuration cache
        callDeployApi(token.get())
    }
}
```

**Configuration time** — some Gradle APIs require a resolved `String` at
configuration time, for example repository credentials:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/myorg/myrepo")
        credentials {
            username = onePassword.property("GITHUB_USER").get()
            password = onePassword.property("GITHUB_KEY").get()
        }
    }
}
```

When `.get()` is called at configuration time, Gradle registers the
`ValueSource` as a configuration cache input. The resolved secret **is stored
in the configuration cache** on disk (see [When secrets are stored to disk](#when-secrets-are-stored-to-disk)).
`op` is called on every build to check whether the cached value is still valid.


### When secrets are stored to disk

Be aware of these situations where a secret may be persisted to disk:

| Situation | Location | Encrypted |
|---|---|---|
| `.get()` called at **configuration time** (e.g. repository credentials) | Gradle configuration cache file under `.gradle/configuration-cache/` | Yes — Gradle encrypts the CC with a per-project key |
| Secret printed to **stdout or stderr** (e.g. `println`) | Gradle daemon log: `<GRADLE_USER_HOME>/daemon/<version>/daemon-*.out.log` | No |
| Build **explicitly writes** the secret to a file | Wherever your build writes it (e.g. `build/`) | No |
| **Build scan** (`--scan`) with secret on stdout | Uploaded to Develocity / scans.gradle.com | No |

**Avoid printing secrets to stdout.** Even when a task runs at execution time,
any value passed to `println` is captured by the Gradle daemon in its log file
(`daemon-*.out.log`), which is stored unencrypted under `GRADLE_USER_HOME`.


## Behavior

- String property values starting with `op://` are resolved lazily through
  `op read` and exposed as `Provider<String>`.
- Plain string values are also exposed as `Provider<String>` — no `op` call is made.
- The resolved value is trimmed before being returned.
- Secret values are never included in plugin error messages.
- The `op://` reference itself (not the secret) is stored in the configuration
  cache as a `ValueSource` parameter.

### Multi-project builds

In a multi-project (monorepo) build, each subproject has its own `ProviderFactory`,
so Gradle's built-in `ValueSource` deduplication does not apply across subprojects.
To avoid redundant `op` invocations the plugin registers a `BuildService` that acts
as a per-build in-memory cache keyed by `op://` reference. Within a single Gradle
build, each unique reference is resolved **at most once**, regardless of how many
subprojects reference it. Because `BuildService` instances are created fresh for
every build (even within the same long-lived daemon), the cache is automatically
cleared between builds, so Gradle's configuration-cache fingerprinting always sees
a fresh `op` call at the start of each new build.


## Configuration

Optional properties in `gradle.properties`:

```properties
# Default: op in PATH
onePassword.op.command=/usr/local/bin/op

# Default: 10000 (10 seconds)
onePassword.op.timeoutMillis=10000
```


## Troubleshooting

- `Unable to execute 1Password CLI command ...`
  Ensure `op` is installed and accessible on `PATH`, or configure `onePassword.op.command`.

- `1Password CLI exited with code ...`
  The CLI command failed (for example, not signed in, missing vault/item/field, or access denied).

- `1Password CLI timed out ...`
  Increase `onePassword.op.timeoutMillis` or investigate local CLI/network conditions.

- `invalid 1Password reference`
  The property value starts with `op://` but does not contain a usable reference.


## Development
### Building and testing

Compile, test and package:

```bash
./gradlew build
```

Run unit tests only:

```bash
./gradlew test
```

Run functional e2e tests:

```bash
./e2e-tests/run-tests
# or
./e2e-tests/run-tests-in-container
```

Run a single spec file (faster, as it reuses shared Gradle daemon and pre-built plugin):

```bash
cd e2e-tests
./run-tests spec/resolve_string_property_spec.sh
```


### Releasing to Gradle Plugin Portal

```bash
gh release create v1.2.0 --generate-notes
```

This will trigger [release workflow](.github/workflows/release-publish.yml) and publish the plugin to the Gradle Plugin Portal.

- The plugin version is read from the release tag (for example `v1.2.3` or `1.2.3`).
- The workflow removes an optional `v` prefix and publishes with `-PreleaseVersion=<resolved-version>`.
- The workflow publishes to the Gradle Plugin Portal using repository secrets `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`.

#### Publish setup

Plugin is released to [Gradle Plugin Portal with user arve0](https://plugins.gradle.org/u/arve0) using an API-key.
API-key is stored in Github secrets:

```shell
gh secret set GRADLE_PUBLISH_KEY
gh secret set GRADLE_PUBLISH_SECRET
```


### Using the plugin locally in another project
#### Option 1: Composite build (recommended, no publishing required)

Use a [composite build](https://docs.gradle.org/current/userguide/composite_builds.html) to include the plugin directly from source.

In the consuming project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("/path/to/gradle-1password-properties-plugin")
}
```

Then apply the plugin as normal — no version number is needed:

```kotlin
plugins {
    id("io.github.arve0.1password.properties")
}
```

##### Using from buildSrc convention plugin (local source)

Use this only when developing/testing the plugin source locally.

Root `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("/path/to/gradle-1password-properties-plugin")
}
```

`buildSrc/settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("/path/to/gradle-1password-properties-plugin")
}
```

`buildSrc/build.gradle.kts`:

```kotlin
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("io.github.arve0.1password.properties:io.github.arve0.1password.properties.gradle.plugin")
}
```

Apply the plugin **without** version in
`buildSrc/src/main/kotlin/my.convention.gradle.kts`:

```kotlin
plugins {
    id("io.github.arve0.1password.properties")
}
```


#### Option 2: Publish to local Maven repository

1. Publish to your local Maven repository (`~/.m2`):

   ```bash
   ./gradlew publishToMavenLocal
   ```

2. In the consuming project's `settings.gradle.kts`, add `mavenLocal()` to the plugin repositories, `settings.gradle.kts`:

   ```kotlin
   pluginManagement {
       repositories {
           mavenLocal()
           gradlePluginPortal()
       }
   }
   ```

3. Apply the plugin with its version:

   ```kotlin
   plugins {
       id("io.github.arve0.1password.properties") version "1.1.1"
   }
   ```
