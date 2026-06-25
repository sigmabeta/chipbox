# `:apps:js`

> The Kotlin/JS browser app — Compose Multiplatform UI in the browser, gated behind `-Pchipbox.js`.

The browser twin of `apps/android` / `apps/jvm`: a single-target Kotlin/JS
executable that mounts the shared `ChipboxAppUi` in a `ComposeViewport`, talks to
`chipbox-server` for library data over HTTP, and runs the chiptune emulators as
Emscripten-compiled WASM. It assembles the `WebChipboxGraph` Metro graph. It's a
leaf — nothing depends on it.

## What this builds / how to run it

- **App target:** a Kotlin/JS (IR) browser executable, webpack-bundled to
  `chipbox.js`. **Requires `-Pchipbox.js=true`** — the shared modules only expose
  a Kotlin/JS variant when that flag is set (see the `sage.kmp.js` plugin), and
  the gate keeps non-web Android/JVM builds from configuring Kotlin/JS at all
  (incompatible with configure-on-demand).
- **Run/build:** `./gradlew :apps:js:jsBrowserDevelopmentRun -Pchipbox.js=true`
  (dev server), or `:apps:js:jsBrowserDistribution -Pchipbox.js=true` for the
  production bundle. `apps/server` copies the bundle into its static resources.
- **WASM emulators:** each `cbox/native/<name>` dir has a CMakeLists with an
  `EMSCRIPTEN` branch; per-emulator `build<Name>Wasm` tasks (needing `$EMSDK` or
  `-Pchipbox.js.emsdk=...`) emit a `.js` loader + `.wasm` blob, and `copyEmulatorWasm`
  fans them into `src/jsMain/resources/wasm/` so webpack serves them at
  `/wasm/<name>.{js,wasm}`. Emulators covered: gme, vgm, ssf, usf, psf, ncsf,
  2sf, vgmstream, gba.

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
  → chipbox-server JSON API), `contentsource/HttpContentSource.kt`,
  `storage/LocalStorageStorage.kt` (sage `Storage` over `window.localStorage`),
  `mediasession/WebMediaSession.kt`, `image/WebImageLoader.kt`,
  `speaker/WebAudioSpeaker.kt`, `analytics/WebAnalytics.kt`, `logging/WebHatchet.kt`.
- **WASM emulators:** `emulators/Wasm*Emulator.kt` (one `Emulator` subclass per
  format) over `wasm/*Wasm.kt` + `*WasmCore.kt` module loaders, plus
  `wasm/WasmModuleLoader.kt` and `wasm/MemfsMirror.kt`.
- **Resources:** `resources/index.html` (splash + mount point),
  `resources/wasm/chipbox-audio-worklet.js`; `webpack.config.d/okio-node-polyfills.js`.

## Module facts

- **Plugin:** `kotlin("multiplatform")` (classpath, unversioned) +
  `compose.compiler` + `compose.multiplatform` + `metro` +
  `kotlin.serialization`. `sage.kmp` / `sage.compose.kmp` are deliberately **not**
  applied (they'd force a `jvm()` + Android target a single-target executable
  doesn't want).
- **Targets:** JS (browser), only when built with `-Pchipbox.js`.
- **Source set:** `src/jsMain/kotlin` (+ `src/jsMain/resources`).
- **SAGE/module dependencies:** `cbox/common` `appui`, `strings`, `repository`,
  `contentsource`, the full `player` chain (director/generator/buffer/cache/
  resampler/speaker — `.real` + `.fake` mix), `playlists`/`favorites`/`scanner`
  (fakes), `settings`/`debug`/`debugInfo`, `models`, `ui.*`; every `features/*`
  `:real` with a non-assisted VM; `sage.common.*` (di, logging, appinfo,
  freeform, list, storage, ui); Ktor JS client, Coil (ktor3), kotlinx
  serialization/coroutines, okio (+ FakeFileSystem), `metrox.viewmodel`,
  JetBrains Compose runtime/foundation/material3/ui.
