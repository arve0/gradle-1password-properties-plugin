# Plan: Better CI Support for One Simple Plugin API

## Goals
- Support all common property sources: `gradle.properties`, environment variable (`ORG_GRADLE_PROJECT_<NAME>`), and `-P`.
- Keep secrets off disk by default.
- Make the plugin easy to use with one documented API.

## Guiding Principles
- Test-first: no production changes until tests describe the desired API and behavior.
- One external API: users should not need to care whether a value came from `op://` or a plain property.
- Secure by default: resolve secrets as late as possible (prefer execution time).
- Readable fixtures: test projects should be easy to inspect and understand in the editor.

## Phase 1: Test Refactor (First)

### 1.1 Fixture layout per spec
- Introduce fixture folders under `e2e-tests/fixtures/`.
- Use at least one fixture per spec; use multiple fixtures when scenarios differ significantly.
- Suggested layout:
  - `e2e-tests/fixtures/api_plain_gradle_properties/`
  - `e2e-tests/fixtures/api_plain_env_var/`
  - `e2e-tests/fixtures/api_plain_cli_P/`
  - `e2e-tests/fixtures/api_op_reference/`
  - `e2e-tests/fixtures/api_mixed_plain_and_op/`
  - `e2e-tests/fixtures/config_cache_execution_time/`

### 1.2 Use fixture files with normal names + envsubst
- Keep standard file names in fixtures so they are editor-friendly:
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `gradle.properties` (when relevant)
- Keep placeholders inside the files and render with `envsubst` into the temp working directory.
- Keep placeholder variables minimal (plugin declaration, local Maven repo URL, `op` mock path, etc.).

### 1.3 Update shared test helpers
- Extend `e2e-tests/spec/spec_helper.sh` with common helper functions:
  - `prepare_fixture <fixture_name>`
  - `render_fixture_file <source> <target>` (via `envsubst`)
  - `set_property_source_mode` for `gradle.properties`, env var, or `-P`
- Replace inline heredoc project setup in specs with fixture-based setup.

### 1.4 Add/update tests for the target API behavior
- Explicitly test the primary user flow:
  1. Apply plugin.
  2. Use plugin for a secret with minimal code.
  3. Continue to work for plain property values.
  4. `op://` values trigger 1Password resolution when consumed.
- Required E2E scenarios:
  - Plain property from `gradle.properties`.
  - Plain property from `ORG_GRADLE_PROJECT_*`.
  - Plain property from `-P`.
  - `op://` from `gradle.properties` (with mocked `op` CLI).
  - Same key from multiple sources with Gradle precedence verified.
- Add a regression test that proves a direct cast approach can fail, so docs/examples do not regress.

### 1.5 Security-focused tests
- Keep and strengthen the test that secrets are not easily searchable on disk.
- Test both:
  - execution-time resolve (expected: not stored in configuration cache)
  - configuration-time resolve (expected: documented tradeoff)
- Ensure tests do not print secret values to stdout unless explicitly needed by the scenario.

## Phase 2: API Design (After Tests Describe the Target)

### 2.1 Define one public API
- Introduce one public plugin API that always returns `Provider<String>`.
- Proposed API name: `onePassword.property("KEY")`.
- The API must behave the same across `gradle.properties`, `ORG_GRADLE_PROJECT_*`, and `-P`.
- Internal behavior:
  - plain string -> provider of plain value
  - `op://` -> lazy provider backed by `op read`

### 2.2 Security defaults
- Design the API for execution-time consumption in task actions.
- Document exactly what happens when `.get()` is called during configuration time.
- Never include secret values in plugin error messages.

## Phase 3: Implementation
- Implement `onePassword.property("KEY")` in plugin code.
- Reuse existing `OpReadValueSource` and resolver logic where possible.
- Keep the implementation small and focused.

## Phase 4: Documentation
- Update README to document only the primary API with concise examples:
  - basic task usage
  - plain property still works
  - `op://` resolves through 1Password on use
- Add a clear section on property sources and precedence.
- Add a security section describing how to avoid writing secrets to disk.
- Do not include legacy or migration notes.

## Phase 5: CI Improvements
- Run E2E in a matrix with at least:
  - Linux + Java LTS + Gradle 8 and 9
  - with and without configuration cache
- Keep `op` CLI mocks stable and deterministic in CI.
- Keep strict secret scanning in CI (fail on matches).

## Acceptance Criteria
- One documented API in README covers all property sources.
- E2E confirms identical behavior for `gradle.properties`, `ORG_GRADLE_PROJECT_*`, and `-P`.
- E2E confirms `op://` still resolves correctly.
- Security tests confirm secrets are not easily searchable on disk in recommended usage.
- Full test suite passes in CI.

## Suggested Execution Order
1. Add fixture folder structure and fixture files.
2. Update `spec_helper.sh` for fixture + `envsubst` flow.
3. Add/update specs for the four core scenarios.
4. Make tests green locally and in CI.
5. Implement the unified API.
6. Update README to the single API.
7. Run full regression and tighten CI checks.
