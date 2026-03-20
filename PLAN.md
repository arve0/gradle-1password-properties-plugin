# Implementation Plan

## Goal
Build a Gradle plugin that resolves project properties prefixed with `op://` using the 1Password CLI, while keeping behavior deterministic, secure, and well-tested.

## Phase 1 - Project setup and plugin wiring
- Configure plugin metadata in `build.gradle.kts` using `java-gradle-plugin`.
- Register plugin id `io.github.arve0.1password.properties` and implementation class.
- Add plugin entrypoint under `src/main/java` implementing `Plugin<Project>`.
- Wire internal components for property resolution and CLI access.

### Acceptance criteria
- `./gradlew tasks` shows plugin metadata correctly.
- Project compiles with plugin classes in place.
- Applying plugin does not fail due to missing wiring.
- Secrets are not easily found in configuration-cache on disk.

## Phase 2 - Property resolution behavior
- Implement resolver logic:
  - If value starts with `op://`, resolve via 1Password CLI.
  - Otherwise return original value unchanged.
- Define behavior for edge cases:
  - missing property,
  - non-string property value,
  - invalid 1Password reference.
- Ensure resolution errors include actionable context (property key and cause).

### Acceptance criteria
- `op://` values always go through resolver path.
- Non-`op://` values pass through unchanged.
- Errors are explicit and deterministic.

## Phase 3 - 1Password CLI integration
- Implement a dedicated `OpCliClient` that executes `op read <reference>`.
- Add handling for:
  - missing CLI,
  - non-zero exit codes,
  - timeouts,
  - empty output.
- Trim result output and return only resolved secret.
- Avoid logging secret values.

### Acceptance criteria
- Successful `op read` resolves secret correctly.
- Failure modes produce controlled exceptions.
- No secret values are exposed in logs or messages.

## Phase 4 - Testing strategy (required)
- Add unit tests for resolver behavior:
  - detection of `op://` references,
  - passthrough behavior,
  - error mapping.
- Add unit tests for CLI client:
  - success,
  - missing binary,
  - timeout,
  - non-zero exit,
  - empty output.
- Add functional tests with Gradle TestKit to verify plugin behavior in a sample build.
- Add/organize test tasks in `build.gradle.kts`:
  - `test` for unit tests,
  - `functionalTest` for TestKit tests,
  - `check` depends on both.

### Acceptance criteria
- `./gradlew test` passes and covers core unit logic.
- `./gradlew functionalTest` validates end-to-end plugin behavior.
- `./gradlew check` runs all test suites and fails on regressions.

## Phase 5 - Documentation and hardening
- Update `README.md` with behavior details and troubleshooting.
- Document expected failures and recovery guidance.
- Confirm example usage remains accurate.

### Acceptance criteria
- README documents usage, behavior, and common errors.
- Plan and implementation stay aligned for first release.

## Delivery checklist
- [x] Plugin scaffold and metadata in place
- [x] `op://` resolution implemented
- [x] CLI client with robust error handling
- [x] Unit tests added and passing
- [x] Functional tests added and passing
- [x] `check` includes all test suites
- [x] README updated for user guidance
