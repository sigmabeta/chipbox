# `:apps:server`

> Headless Ktor HTTP server — scans a library and serves it (and the apps/js bundle) as a JSON API.

A JVM server that reuses the same scan + DB stack as `apps/cli` and exposes the
library over a Ktor/Netty JSON API, plus file streaming and (optionally) the
`apps/js` web bundle. It backs the browser app's `RemoteRepository`. It assembles
the `ServerChipboxGraph` Metro graph and is a leaf — nothing depends on it.

## What this builds / how to run it

- **App target:** the `chipbox-server` application (`mainClass =
  net.sigmabeta.chipbox.server.MainKt`), via the Gradle `application` plugin.
- **Run:** `CHIPBOX_LIBRARY_DIR=/music ./gradlew :apps:server:run`. Config comes
  from env vars (`ServerConfig.fromEnv`): `CHIPBOX_LIBRARY_DIR` is required;
  `CHIPBOX_PORT` (default `8080`), `CHIPBOX_BIND_HOST` (default `0.0.0.0`),
  `CHIPBOX_WORK_DIR`, `CHIPBOX_DB_PATH`, `CHIPBOX_CORS_ALLOWED_ORIGINS` are
  optional. Missing/invalid library dir fails fast with exit code 64.
- **Native lib:** like `apps/cli`, the scanner needs only `libvgmstream.so`
  (subsong probe); the build depends on `:apps:jvm:nativeEmulatorVgmstream` and
  points `java.library.path` at `apps/jvm/libs` (bundled into `lib/native/` in
  the distribution). No emulator playback on the server.
- **Web bundle:** default `:apps:server:run` is webpack-free (serve the
  webpack-dev-server separately). Opt in with `-Pchipbox.server.bundleJs`
  (`+ -Pchipbox.server.productionBundle` for minified). The shippable
  `installDist`/`distZip`/`distTar` artifacts always bundle the production JS —
  which requires `-Pchipbox.js=true` (apps/js is gated behind it) or they fail loudly.

## Entry point

`Main.kt`'s `main()` reads `ServerConfig.fromEnv`, builds the `ServerChipboxGraph`,
seeds the configured library dir into the persisted locations, launches a
background scan (the server serves immediately — endpoints return whatever is in
the DB now), then starts `embeddedServer(Netty, ...)` with content negotiation,
status pages, partial content (Range), CORS, call logging, and the `/api/health`
+ `libraryRoutes` + `fileRoutes` + `staticRoutes` routing.

## Contents

| File | What it is |
| --- | --- |
| `Main.kt` | Entry point: config → graph → background scan → Ktor/Netty server + routing. |
| `ServerConfig.kt` | Env-var-sourced runtime config; fails fast (exit 64) on a missing library dir. |
| `di/ServerChipboxGraph.kt` | `@DependencyGraph(AppScope)` root; accessors for repository, scanner, content sources, app scope. Factory takes `dbPath` + `workDir`. |
| `di/ServerModules.kt` | Binding containers composing the DB/scanner/repository/coroutines wiring. |
| `http/LibraryRoutes.kt`, `http/FileRoutes.kt`, `http/StaticRoutes.kt` | The `/api` JSON endpoints, file streaming (`/api/files/by-path`), and SPA-fallback static serving. |
| `http/ImageResizer.kt`, `http/PublicUrls.kt`, `http/DataFlowDrain.kt` | Thumbnail resizing, public URL rewriting, repository `Data`-flow draining helper. |
| `logging/ServerHatchet.kt` | `Hatchet` impl routing through the server's logger. |

## Module facts

- **Plugin:** `sage.jvm` + `kotlin.serialization` + `metro` + `application`.
- **Targets:** JVM only.
- **Source set:** `src/main/java` (`jvmSharedMain`).
- **SAGE/module dependencies:** `cbox/common` `scanner`, `repository`,
  `database`, `contentsource(.file)`, `readers`, `models`, `strings`,
  `player.emulators.vgmstream.real` (api + real); `sage.common.di` / `logging` /
  `ui.strings`; Ktor server (netty, content-negotiation, status-pages, cors,
  partial-content, auto-head, call-logging), kotlinx serialization/coroutines,
  bundled SQLite, logback, okio. Build-time: `:apps:jvm:nativeEmulatorVgmstream`
  and (for shipping) `:apps:js:jsBrowserDistribution`.
