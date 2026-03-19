# gradle-1password-properties-plugin

This plugin resolves Gradle project properties prefixed with `op://` by calling the 1Password CLI (`op read <reference>`).


## Usage

`gradle.properties`:

```properties
GITHUB_TOKEN=op://Personal/Github Personal Access Token/token
```

`build.gradle.kts`:

```kotlin
plugins {
    id("io.github.arve0.1password.properties") version "0.1.0"
}

val githubToken: String by project

print("Resolved GitHub token: $githubToken")
```

### Configuration cache

This plugin is compatible with the [configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html). 
When configuration cache is enabled, the secrets are stored in the 
configuration cache and reused on later builds. The 1Password CLI is 
not called again until the cache is invalidated.

If you need to update secrets, invalidate the cache when running gradle:

```shell
gradle --no-configuration-cache clean build
```


## Behavior

- String property values starting with `op://` are resolved through `op read`.
- String property values not starting with `op://` are left unchanged.
- The resolved value is trimmed before being set as the project property.
- Secret values are never included in plugin error messages.
- Secret values are stored to the configuration cache.


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

```bash
./gradlew build
```

Run unit tests only:

```bash
./gradlew test
```

Run functional tests only:

```bash
./gradlew functionalTest
```


### Releasing to Gradle Plugin Portal

```bash
gh release create v0.1.0 --generate-notes
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
       id("io.github.arve0.1password.properties") version "0.1.0"
   }
   ```
