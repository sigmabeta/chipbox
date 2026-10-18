# `:apps:server`

> Headless Ktor HTTP server — scans a library and serves it (and the apps/js bundle) as a JSON API.

A JVM server that reuses the same scan + DB stack as `apps/cli` and exposes the
library over a Ktor/Netty JSON API, plus file streaming and (optionally) the
`apps/js` web bundle. It backs the browser app's `RemoteRepository`. It assembles
the `ServerChipboxGraph` Metro graph and is a leaf — nothing depends on it.

> **No authentication.** The server is for loopback or trusted-LAN use only. Do
> not publish an image or expose it to the internet until an auth story exists
> (shared token or reverse-proxy auth). This is a hard release gate; see
> [ADR-0003](../../docs/adr/0003-web-app-self-hosted-browser-client.md).

## What this builds / how to run it

- **App target:** the `chipbox-server` application (`mainClass =
  net.sigmabeta.chipbox.server.MainKt`), via the Gradle `application` plugin.
- **Run:** `CHIPBOX_LIBRARY_DIR=/music ./gradlew :apps:server:run`. Config comes
  from env vars (`ServerConfig.fromEnv`): `CHIPBOX_LIBRARY_DIR` is required;
  `CHIPBOX_PORT` (default `8080`), `CHIPBOX_BIND_HOST` (default `0.0.0.0`),
  `CHIPBOX_WORK_DIR`, `CHIPBOX_DB_PATH`, `CHIPBOX_CORS_ALLOWED_ORIGINS` are
  optional. Missing/invalid library dir fails fast with exit code 64. Binding a
  non-loopback host logs a no-auth warning at startup.
- **Web bundle:** default `:apps:server:run` is webpack-free (serve the
  webpack-dev-server separately). Opt in with `-Pchipbox.server.bundleJs`
  (`+ -Pchipbox.server.productionBundle` for minified). The shippable
  `installDist`/`distZip`/`distTar` artifacts always bundle the production JS —
  which requires `-Psage.js=true` (apps/js is gated behind it) plus the web
  flags (`-Dorg.gradle.isolated-projects=false
  -Dorg.gradle.configureondemand=false --no-parallel`) or they fail loudly.
- **Health / readiness:** `GET /api/health` returns
  `{ok, state:{status,timeInSeconds,failedFile?}, games, tracks, tracksFailed, error?}`.
  `ok` is false only for a failed scan; `status` is `unknown|idle|scanning|complete|failed`.
  No host path is exposed (only a failed file's basename). The browser retries
  its empty first library read while `status` is `scanning` (bounded retry), so
  opening the client during the startup scan shows the library once it finishes.
- **Library refresh is restart-only.** The server scans on every start in the
  background and serves the current DB immediately. Unchanged folders are skipped
  by signature + scanner/reader version; new/changed files are picked up on
  restart. There is no rescan endpoint, file watcher, or periodic scan; folder
  management and scan progress are admin concerns, not web UI.
- **Native lib:** the scanner needs `libvgmstream` for its subsong probe. It is
  host-built by `:apps:jvm:buildHostNativeVgmstream` (CMake, no committed binary)
  into `apps/jvm/build/jvm-native/out/<target>/vgmstream/host/` and exposed as
  apps/jvm's `hostVgmstreamElements` configuration. `run` and the distributions
  consume that configuration, so a fresh checkout builds it. Packaged dists copy
  `libvgmstream.{so,dylib,dll}` into `lib/native/` and the launch scripts point
  `java.library.path` there. Check it directly with
  `./gradlew :apps:server:verifyVgmstreamNativeLib
  -Dorg.gradle.isolated-projects=false --configure-on-demand
  -Pchipbox.cmake=$(command -v cmake)`. No emulator playback on the server.
- **Docker image:** the deployment artifact is the root `Dockerfile` (multi-stage:
  JDK 21 + Node 22/Yarn + pinned emsdk builds the bundle/WASM, the native stage
  builds the target-arch `libvgmstream.so`, a JRE 21 runtime runs non-root). Build
  with the `sage` submodule checked out:
  ```sh
  git submodule update --init --recursive
  docker build -t chipbox-server:dev .
  docker run --rm -p 127.0.0.1:8080:8080 \
    -v /path/to/music:/music:ro -v chipbox-data:/data chipbox-server:dev
  ```
  `/music` is read-only; `/data` persists the DB + work dir (a bind-mounted
  `/data` must be writable by uid `10001`). The image is built for amd64 on PRs
  (and both amd64/arm64 on release) but **is not published to any registry**.
  There is **no authentication**: run it on loopback or a trusted LAN only.

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
| `http/LibraryRoutes.kt`, `http/FileRoutes.kt`, `http/StaticRoutes.kt` | The `/api` JSON endpoints, file streaming (`/api/files/{trackId}`, chain files, covers/photos), and SPA-fallback static serving. |
| `http/Health.kt` | `/api/health`: maps the live `ScannerState` to the shared `HealthStatus` wire model (no host paths). |
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
  bundled SQLite, logback, okio. Build-time:
  `hostVgmstreamElements` from `:apps:jvm` (CMake-produced; see the native-lib
  section) and (for shipping) `:apps:js:jsBrowserDistribution` + the WASM
  bundle. Tests: `:apps:server:test` covers the health mapping and loopback-bind
  detection (plain JVM, no server startup).
