# Plan: raskere e2e-tester

Nåværende kjøretid er ~3–6 minutter. Det meste av tiden brukes på tre ting:

1. **Gradle wrapper laster ned distribusjon** (~20 s første gang per `GRADLE_USER_HOME`)
2. **Gradle daemon starter fra scratch for hvert testprosjekt** (~5–15 s per test)
3. **Plugin kompileres på nytt via `includeBuild` for hvert testprosjekt** (~5–10 s per test)
4. **Testene kjører sekvensielt** (8 tester × ~40 s = ~5 min)

---

## Tiltak 1: Del én Gradle daemon mellom alle tester

**Problem:** Hver test bruker sin egen `FIXTURE_DIR/gradle-home`, som betyr en ny daemon per test.

**Løsning:** Opprett ett delt `GRADLE_USER_HOME` for hele testkjøringen i `spec_helper.sh`:

```sh
# spec_helper.sh – legg til øverst i suite-setup
SHARED_GRADLE_HOME=
suite_setup() {
  SHARED_GRADLE_HOME="$(mktemp -d)/gradle-home"
}
suite_teardown() {
  GRADLE_USER_HOME="$SHARED_GRADLE_HOME" "$PROJECT_ROOT/gradlew" --stop >/dev/null 2>&1 || true
  rm -rf "$SHARED_GRADLE_HOME"
}
```

Endre `run_gradle_capture` til å bruke `SHARED_GRADLE_HOME` i stedet for `FIXTURE_DIR/gradle-home`.

Registrer i `spec_helper_configure()`:
```sh
shellspec_configure() {
  before_all 'suite_setup'
  after_all 'suite_teardown'
}
```

**Forventet gevinst:** Daemon starter én gang, ikke 8 ganger. Sparer ~10–15 s per test → ~60–90 s totalt.

---

## Tiltak 2: Forhåndskompiler plugin én gang (unngå `includeBuild` per test)

**Problem:** Hvert testprosjekt bruker `includeBuild("$PROJECT_ROOT")`, som trigger kompilering av plugin-koden i konfigurasjonsfasen for hvert prosjekt.

**Løsning:** Publiser plugin til et lokalt Maven-repo én gang før testene starter, og bruk det i stedet for `includeBuild`.

```sh
# suite_setup – kjør én gang
suite_setup() {
  SHARED_GRADLE_HOME="$(mktemp -d)/gradle-home"
  LOCAL_MAVEN_REPO="$(mktemp -d)/m2"
  (
    cd "$PROJECT_ROOT" || exit 1
    GRADLE_USER_HOME="$SHARED_GRADLE_HOME" \
      ./gradlew publishToMavenLocal -Dmaven.repo.local="$LOCAL_MAVEN_REPO" -q
  )
}
```

Endre `settings.gradle.kts`-malen i `setup_fixture` til:
```kotlin
pluginManagement {
    repositories {
        maven { url = uri("$LOCAL_MAVEN_REPO") }
        gradlePluginPortal()
    }
}
```

**Forventet gevinst:** Plugin kompileres én gang i stedet for 8. Sparer ~5–10 s per test → ~40–70 s totalt.

---

## Tiltak 3: Kjør tester parallelt med `--jobs`

**Problem:** ShellSpec kjører spec-filer sekvensielt som standard.

**Løsning:** Legg til `--jobs` i `.shellspec` og sørg for at hver test har isolert `TMP_DIR` (det har de allerede via `mktemp -d` i `setup_fixture`).

```sh
# e2e-tests/.shellspec
--require spec_helper
--format documentation
--jobs 4
```

Eller i `run-tests-locally` / `run-tests-in-container`:
```sh
shellspec --jobs "$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"
```

**Forutsetning:** Tiltakene 1 og 2 må bruke thread-safe paths. `SHARED_GRADLE_HOME` og `LOCAL_MAVEN_REPO` er read-only etter suite-setup, så det er trygt. Hvert test har sin egen `TMP_DIR` og `FIXTURE_DIR`.

**Forventet gevinst:** 4 parallelle jobber gir ~4× raskere kjøring av de uavhengige testene. Fra ~3 min til ~1 min.

---

## Tiltak 4: Cache Gradle wrapper-nedlastning i Docker-imaget

**Problem:** Første kjøring i en fersk container laster ned Gradle-distribusjonen (~50 MB, ~20 s).

**Løsning:** Legg til pre-warming av wrapper i `Dockerfile`:

```dockerfile
# e2e-tests/Dockerfile
COPY gradlew gradlew
COPY gradle/ gradle/
RUN ./gradlew --version   # laster ned og cacher distribusjonen i imaget
```

**Forventet gevinst:** Eliminerer nedlastning ved hver `docker run`. Sparer ~20 s per kjøring, og gjør feedback i CI forutsigbar.

---

## Sammendrag

| Tiltak | Implementasjonskostnad | Forventet gevinst |
|---|---|---|
| 1. Delt Gradle daemon | Lav – endre `GRADLE_USER_HOME` i helper | ~60–90 s |
| 2. `publishToMavenLocal` i suite-setup | Medium – endre `settings.gradle.kts`-mal | ~40–70 s |
| 3. Parallell kjøring med `--jobs` | Svært lav – én linje i `.shellspec` | ~2–3× raskere |
| 4. Cache wrapper i Docker-image | Lav – to linjer i `Dockerfile` | ~20 s i Docker |

Anbefalt rekkefølge: **3 → 4 → 1 → 2**. Tiltak 3 og 4 er trivielle og gir umiddelbar effekt uten risiko for test-isolasjon. Tiltak 1 og 2 gir størst gevinst men krever litt mer testing av at isolasjonen holder.
