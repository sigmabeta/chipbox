# Multiplatform structure

Current-state map of the KMP layout. The Android→KMP migration is complete; this
doc is the end-state reference. Trust the code when it disagrees.

Targets: `androidTarget` + `jvm()` everywhere; some modules also add
`js { nodejs() }` as a purity-enforcement target. JVM 17. compileSdk 36 /
minSdk 26.

## Top-level layout

```
apps/        application targets: android, jvm (desktop+headless), cli, js, server
cbox/
  common/    KMP library code (builds for android + jvm) — the bulk of the app
  android/   Android-only system glue + the :native CMake companions
  native/    C/C++ emulator source trees (built by both Android NDK and host CMake)
features/    user-facing screens, all KMP (api/real/screenshot)
build-logic/ chipbox-specific convention plugins (id "chipbox.*")
sage/        vendored SAGE submodule: convention plugins + version catalog + libs
scripts/     helpers (verify.sh CI suite, loc_report.py)
```

## Module suffix convention

A logical module splits into variants by suffix:

- `:api` — interfaces, types, route keys. Always KMP (`sage.kmp`).
- `:real` — production impl. Usually KMP; uses `androidMain`/`jvmMain` only for
  genuine platform seams (e.g. `speaker/real` = AudioTrack on Android,
  SourceDataLine on JVM).
- `:di` — Metro binding container. (Historically Hilt-only/`sage.android`; with
  Metro these are plain binding containers — see `sage-integration.md`.)
- `:fake` — test doubles, KMP, consumed from `commonTest`.
- `:native` — Android-only companion that runs CMake via `externalNativeBuild`
  and packs the `.so` into the APK. Lives under `cbox/android/player/emulators/<emu>/native`.
- `:screenshot` — Paparazzi snapshot tests (Android-only). Lives under `features/<x>/screenshot`.

## Source sets — where code physically lives

The `sage.kmp` plugin defines a custom intermediate source set so that legacy
`java.*`-using code compiles for both targets with **no file moves**:

| Directory | Role | Compiles into |
|---|---|---|
| `src/commonMain/kotlin/` | pure Kotlin, no `java.*`/`android.*` | android + jvm |
| `src/main/java/` | mapped to **`jvmSharedMain`**; `java.*` allowed | android + jvm |
| `src/androidMain/kotlin/` | Android-only / `actual` | android |
| `src/jvmMain/kotlin/` | JVM-only / `actual` | jvm |
| `src/test/java/` | mapped to `jvmSharedTest` | androidTest + jvmTest |
| `src/commonTest/kotlin/` | pure test code | androidTest + jvmTest |

`androidMain` and `jvmMain` both `dependsOn(jvmSharedMain)`; `jvmSharedMain
dependsOn(commonMain)`. So the rule is:

- Pure Kotlin → `src/commonMain/kotlin/`.
- Needs `ConcurrentHashMap` / `RandomAccessFile` / `ByteBuffer` / `String.format`
  / Room DAOs etc. → leave in `src/main/java/` (jvmSharedMain).
- Genuinely Android (Context, AudioTrack, SAF) → `src/androidMain`.
- Genuinely JVM (javax.sound, Swing, java.io.File walking) → `src/jvmMain`.

Convert a `sage.android`/`sage.jvm` module to KMP first (plugin swap, no moves),
then hoist pure files from `src/main/java/` up to `src/commonMain/kotlin/`
incrementally. Audit for `import java.*`/`javax.*`/`android.*` **and**
`\bR\.(string|font|drawable|color)` (the generated `R` class has no import line)
before hoisting.

## Convention plugins

SAGE base plugins (in `sage/sage-build-logic/convention/src/main/kotlin/`,
applied as `alias(libs.plugins.sage.*)`):

- `sage.kmp` (`SageKmpModulePlugin`) — `kotlin.multiplatform` + AGP 9
  `com.android.kotlin.multiplatform.library` (note: the old `com.android.library`
  is rejected alongside the KMP plugin in AGP 9), the `jvmSharedMain`/`jvmSharedTest`
  source sets, JVM 17, detekt, warnings-as-errors, coroutines opt-in.
- `sage.jvm` / `sage.android` — single-target Kotlin/JVM or Android library.
- `sage.compose.kmp` / `sage.compose.android` — layer Compose Multiplatform
  (runtime/foundation/material3/ui + compiler plugin) over the above.
- `sage.di` — Metro compiler plugin + `sage.common.di` dep.

Chipbox plugins (in `build-logic/convention/src/main/kotlin/`, applied as
`id("chipbox.*")`):

- `chipbox.feature.api` — `sage.kmp` + serialization. Use for `features/*/api`.
- `chipbox.feature.real` — `sage.kmp` + `sage.compose.kmp` + Metro. Use for `features/*/real`.
- `chipbox.emulator.real` — `sage.kmp`; auto-wires the `:native` companion as a
  `runtimeOnly` android dep.
- `chipbox.emulator.native` — `sage.android`; derives the CMake path from the
  directory name.
- `chipbox.screenshot` — `sage.android` + `sage.compose.android` + Paparazzi.
- `chipbox.kmp.test` — adds `kotlin("test")` + coroutines-test to `commonTest`.

Namespaces are set per-module in `kotlin { androidLibrary { namespace = "…" } }`
(derived from the Gradle path, e.g. `:cbox:common:player:director:real` →
`net.sigmabeta.chipbox.common.player.director.real`).

### Example `sage.kmp` build file

`cbox/common/player/director/real/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.sage.kmp)
    id("chipbox.kmp.test")
}

kotlin {
    js { nodejs() }
    androidLibrary { namespace = "net.sigmabeta.chipbox.common.player.director.real" }

    sourceSets {
        named("commonMain") {
            dependencies {
                api(projects.cbox.common.player.director.api)
                implementation(projects.cbox.common.player.generator.api)
                api(libs.kotlinx.coroutines.core)
                implementation(libs.sage.common.logging)
            }
        }
        named("commonTest") {
            dependencies {
                implementation(projects.cbox.common.player.generator.fake)
            }
        }
    }
}
```

Note: dependencies go inside `kotlin { sourceSets { named("…") { dependencies } } }`,
not a top-level `dependencies {}` block (the common KMP gotcha). For
`jvmSharedMain`-resident (`src/main/java`) deps, use
`named("jvmSharedMain") { dependencies { … } }`.

## Apps

- `apps/android` — the Android app. Plugin: `com.android.application` (NOT KMP).
  Builds the Metro `@DependencyGraph`, hosts the shared Compose UI
  (`cbox/common/appui/api`), depends on every `features/*/real`.
- `apps/jvm` — desktop (Compose Multiplatform window via `--args="gui"`) +
  headless CLI render modes (`scan`/`play`/single-file). `sage.jvm + application`.
  Host-builds native libs via CMake into `apps/jvm/libs`. Has its own Metro graph
  + JVM impls (`JvmStorage`, `JvmStringProvider`, etc.).
- `apps/cli` — Mordant TUI scanner/organizer; reuses the jvm app's vgmstream
  native task.
- `apps/js`, `apps/server` — minimal/experimental targets.

## Native code

Source trees live in `cbox/native/<emu>/` (one per format) + `native-common`.
The same CMake tree builds two ways:

- **Android**: the `:native` companion module
  (`cbox/android/player/emulators/<emu>/native`, plugin `chipbox.emulator.native`)
  points `externalNativeBuild` at `cbox/native/<emu>/CMakeLists.txt`. AGP 9's KMP
  library extension has no `externalNativeBuild` DSL, which is why this is a
  separate Android-only module from the KMP `:real` JNI wrapper.
- **JVM**: `apps/jvm` host-builds the same tree into `apps/jvm/libs/lib<emu>.so`
  and loads it via `-Djava.library.path`.

`2sf` is special: the Gradle project name is `twosf` (identifiers can't start
with a digit); `settings.gradle.kts` remaps `projectDir`.

## Adding a KMP module — checklist

1. Create dirs; put pure code in `src/commonMain/kotlin/`, `java.*` code in
   `src/main/java/`.
2. Write `build.gradle.kts` with the right `chipbox.*` / `sage.*` plugin and the
   `androidLibrary { namespace }` block.
3. Register the project path(s) in `settings.gradle.kts`.
4. Wire it into consumers (`apps/android`, `apps/jvm`, other modules) via
   `implementation(projects.…)` typesafe accessors.
5. Build both variants: `./gradlew :path:to:module:build`; then `ktlintCheck detekt`.
