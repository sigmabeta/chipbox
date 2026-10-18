# `:apps:js`

> The Kotlin/JS browser app — Compose Multiplatform UI in the browser, gated behind `-Psage.js`.

The browser twin of `apps/android` / `apps/jvm`: a single-target Kotlin/JS
executable that mounts the shared `ChipboxAppUi` in a `ComposeViewport`, talks to
`chipbox-server` for library data over HTTP, and runs the chiptune emulators as
Emscripten-compiled WASM. It assembles the `WebChipboxGraph` Metro graph. It's a
leaf — nothing depends on it.

v1 is a single-user, self-hosted client: the server owns the library, the browser
owns playback and all durable user state (`localStorage`). There is no auth yet,
so use is limited to loopback/trusted LAN. See
[ADR-0003](../../docs/adr/0003-web-app-self-hosted-browser-client.md) and
[web-app.md](../../arch-docs/architecture/web-app.md), which also holds the v1
acceptance checklist.

## What this builds / how to run it

- **App target:** a Kotlin/JS (IR) browser executable, webpack-bundled to
  `chipbox.js`. **Requires `-Psage.js=true`** — the shared modules only expose
  a Kotlin/JS variant when that flag is set (see the `sage.kmp.js` plugin), and
  the gate keeps non-web Android/JVM builds from configuring Kotlin/JS at all.
- **Toolchain:** system **Node 22** and **Yarn 1.x** (Kotlin's own Node/Yarn
  download is disabled). Pinned versions live in `docker/node-version` and
  `docker/yarn-version`; CI and the Docker image install exactly those. The
  **Emscripten SDK** is pinned in `docker/emsdk-version` (4.0.7) and must be
  activated (`upstream/emscripten/emcc` present).
- **Run/build:** every web invocation needs Isolated Projects off and
  configure-on-demand off (Kotlin/JS + webpack resolve cross-project npm
  configurations), and `--no-parallel` (the npm lock is not parallel-safe):
  ```sh
  ./gradlew :apps:js:jsBrowserDevelopmentRun -Psage.js=true \
    -Dorg.gradle.isolated-projects=false -Dorg.gradle.configureondemand=false --no-parallel
  ./gradlew :apps:js:jsBrowserDistribution -Psage.js=true \
    -Dorg.gradle.isolated-projects=false -Dorg.gradle.configureondemand=false --no-parallel
  ```
  Do **not** pass `--no-configuration-cache`: Isolated Projects cannot be
  disabled while the configuration cache is off. `apps/server` copies the
  production bundle into its static resources.
  - If a build fails with "Lock file was changed", run
    `./gradlew kotlinUpgradeYarnLock -Psage.js=true -Dorg.gradle.isolated-projects=false -Dorg.gradle.configureondemand=false --no-parallel`
    and commit the refreshed `kotlin-js-store/yarn.lock`.
- **WASM emulators (automatic):** each `cbox/native/<name>` dir has a CMakeLists
  with an `EMSCRIPTEN` branch; per-emulator `build<Name>Wasm` tasks emit a `.js`
  loader + `.wasm` blob, and `copyEmulatorWasm` fans them into
  `src/jsMain/resources/wasm/`. **`jsProcessResources` depends on that copy**, so
  every bundle path (`jsBrowserDistribution`, the dev server, and the server's
  `installDist` bundle) ships them at `/wasm/<name>.{js,wasm}`. The emulator list
  is `apps/js/wasm-emulators.txt` (one `dir|output|display` line each), shared
  with the bundle check so the build and the assertion cannot drift.
  - emsdk discovery: `-Psage.js.emsdk=/path/to/emsdk`, then `$EMSDK`.
    `:apps:js:checkEmsdk` runs at execution and **fails the bundle build** when
    emsdk is missing or not activated — it can no longer silently produce an
    emulator-less bundle. Unrelated tasks (`compileKotlinJs`, tests) still work
    without emsdk.
  - Verify the artifact: `./gradlew :apps:js:verifyWebBundle -Psage.js=true ...`
    (builds the production distribution, then asserts `index.html`, `chipbox.js`,
    and all nine `.js` + `.wasm` pairs), or run the standalone script CI/Docker
    use: `scripts/ci/verify-web-bundle.sh [bundle-dir]`.
  - Emulators covered: gme, vgm, ssf, usf, psf, ncsf, 2sf, vgmstream, gba. WASM
    outputs are gitignored and never committed.

## Entry point

`JsMain.kt`'s `main()` builds a `WebHatchet` + Coil image loader, then in a
`MainScope().launch` preloads i18n strings (suspend), builds the
`WebChipboxGraph`, installs the browser `WebMediaSession`, wires Escape→back and
arrow-key→focus handling, removes the `index.html` splash, and mounts
`ChipboxAppUi` in a `ComposeViewport`.

## Contents (grouped by role)

- **Entry / DI:** `JsMain.kt` (browser `main()`); `di/WebChipboxGraph.kt`
  (`@DependencyGraph(AppScope)`, factory takes caller-built `Hatchet` +
  `StringProvider`), `di/WebModules.kt` (binds fakes for everything needing
  native code or platform persistence), `di/WebMetroViewModelFactory.kt`.
- **Data / platform services:** `repository/RemoteRepository.kt` (Ktor JS client
  → chipbox-server JSON API; list reads retry while the startup scan is running,
  bounded by the shared `ScanRetryPolicy`), `contentsource/HttpContentSource.kt`,
  `storage/LocalStorageStorage.kt` (sage `Storage` over `window.localStorage`),
  `mediasession/WebMediaSession.kt`, `image/WebImageLoader.kt`,
  `speaker/WebAudioSpeaker.kt`, `analytics/WebAnalytics.kt`, `logging/WebHatchet.kt`.
- **WASM emulators:** `emulators/Wasm*Emulator.kt` (one `Emulator` subclass per
  format) over `wasm/*Wasm.kt` + `*WasmCore.kt` module loaders, plus
  `wasm/WasmModuleLoader.kt` and `wasm/MemfsMirror.kt`.
- **Resources:** `resources/index.html` (splash + mount point),
  `resources/wasm/chipbox-audio-worklet.js`; `wasm-emulators.txt` (the nine WASM
  modules shared by the build and the bundle check); `webpack.config.d/okio-node-polyfills.js`.

## Module facts

- **Plugin:** `kotlin("multiplatform")` (classpath, unversioned) +
  `compose.compiler` + `compose.multiplatform` + `metro` +
  `kotlin.serialization`. `sage.kmp` / `sage.compose.kmp` are deliberately **not**
  applied (they'd force a `jvm()` + Android target a single-target executable
  doesn't want).
- **Targets:** JS (browser), only when built with `-Psage.js`.
- **Source set:** `src/jsMain/kotlin` (+ `src/jsMain/resources`).
- **SAGE/module dependencies:** `cbox/common` `appui`, `strings`, `repository`,
  `contentsource`, the full `player` chain (director/generator/buffer/cache/
  resampler/speaker — `.real` + `.fake` mix), `playlists`/`favorites`/`scanner`
  (fakes), `settings`/`debug`/`debugInfo`, `models`, `ui.*`; every `features/*`
  `:real` with a non-assisted VM; `sage.common.*` (di, logging, appinfo,
  freeform, list, storage, ui); Ktor JS client, Coil (ktor3), kotlinx
  serialization/coroutines, okio (+ FakeFileSystem), `metrox.viewmodel`,
  JetBrains Compose runtime/foundation/material3/ui.
