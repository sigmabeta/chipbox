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

## RSN extraction

RSN is a solid RAR archive of SPC rips, and browsers have no RAR decoder. The
server graph is the only place a scanner gets a `ServerRsnExtractor`; it writes
each archive's SPC members to:

```text
<CHIPBOX_WORK_DIR>/rsn/<sha256(archive bytes)>/<sanitized-member>.spc
```

- Content-addressed and deterministic: the same archive bytes map to the same
  paths regardless of archive filename or library folder. Member names have
  directory components stripped and unsafe characters replaced; duplicate
  basenames get a zero-padded member-index prefix (`00-01 Theme.spc`,
  `01-01 Theme.spc`).
- Extraction runs after the m3u overlay and filename-title fallback, so an m3u
  entry naming `archive.rsn` + a 1-based member index still matches the right
  member. The persisted track is then ordinary: `path` is the extracted `.spc`,
  `extension` is `spc`, and `trackNumber` is `0`.
- A member is written only when it is missing or its bytes mismatch; matching
  content-addressed output is reused.
- Orphaned hash directories are pruned only after a fully successful scan
  (`tracksFailed == 0`) and after removed folders are pruned. A directory is kept
  whenever any surviving track path is under it — including folders the scan
  skipped — and delete failures are logged and retried on the next successful
  scan. A failed or partial scan never deletes usable output.
- Desktop, Android, and CLI pass no extractor and keep staging RSN members out
  of the archive at playback time.
- `CHIPBOX_WORK_DIR` is an installation-stable part of the track identity.
  Moving it or deleting its contents invalidates extracted track paths:
  browser-local favorites, playlists, history, and queues that reference those
  track IDs become unknown and must be dropped during hydration rather than
  failing. A scan that re-reads the folder re-extracts the members, but with an
  intact DB the folder-skip signature does not notice a cleared work dir, so
  previously indexed RSN tracks 404 until that folder is re-read. Keep the work
  dir across restarts.
- Scanner version 2 forces RSN rows written before extraction to be re-read once
  on upgrade. Later extraction/naming changes need another scanner bump (or a
  dedicated stored extraction version). Track IDs re-key once on the first
  extraction (archive path + member index → extracted path + `0`).

## Tests

`./gradlew :apps:server:test` runs the server tests. The RSN suite covers
extraction to ordinary SPC tracks, determinism/reuse, m3u overlay matching,
the pruning gate, and serving/path-leak via `/api/files/{id}` +
`/api/tracks/{id}`. Fixtures are generated as stored-entry RAR4 archives in a
test helper (no external `rar` tool), and the scan uses
`DatabaseRepository` over `FakeDatabase` with a no-op vgmstream prober, so no
native library is required.

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
| `scanner/ServerRsnExtractor.kt` | Content-addressed RSN→SPC extraction + post-scan orphan pruning. |
| `logging/ServerHatchet.kt` | `Hatchet` impl routing through the server's logger. |

## Module facts

- **Plugin:** `sage.jvm` + `kotlin.serialization` + `metro` + `application`.
- **Targets:** JVM only.
- **Source set:** `src/main/java` (`jvmSharedMain`); tests in `src/test/kotlin`.
- **SAGE/module dependencies:** `cbox/common` `scanner`, `repository`,
  `database`, `contentsource(.file)`, `readers`, `models`, `utils`, `strings`,
  `player.emulators.vgmstream.real` (api + real); `sage.common.di` / `logging` /
  `ui.strings`; Ktor server (netty, content-negotiation, status-pages, cors,
  partial-content, auto-head, call-logging), kotlinx serialization/coroutines,
  bundled SQLite, logback, okio. Build-time:
  `hostVgmstreamElements` from `:apps:jvm` (CMake-produced; see the native-lib
  section) and (for shipping) `:apps:js:jsBrowserDistribution` + the WASM
  bundle. Tests: `:apps:server:test` covers the health mapping and loopback-bind
  detection (plain JVM, no server startup), plus scan/RSN extraction over
  `DatabaseRepository`/`FakeDatabase` and route tests through Ktor's test host.
