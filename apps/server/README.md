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
  `{ok, state:{status,timeInSeconds,failedFile?}, games, tracks, tracksFailed}`.
  `ok` is false only for a failed scan; `status` is `unknown|idle|scanning|complete|failed`.
  No host path is exposed (only a failed file's basename). The browser paints a partial
  first library read immediately, then re-reads when `status` leaves `scanning` (bounded
  retry); an empty first read waits for the scan, and an initial `unknown` also retries,
  so opening the client during the startup scan converges on the full library.
- **Browsing / paging:** `GET /api/games`, `GET /api/artists`, and
  `GET /api/tracks` all accept `limit` and `offset`.
  - `offset` defaults to `0`; a negative value clamps to `0`; an offset too large
    to fit in an `Int` clamps to `Int.MAX_VALUE` (an empty page, not page one).
  - An absent `limit` returns every row from `offset` onward. A present `limit`
    is clamped to `1..500`; a non-numeric value becomes `500`. This keeps a
    typo'd or absurd request from streaming the whole catalog. Invalid values are
    coerced, not rejected — no 400 for paging parameters.
  - `?ids=1,2,3` bypasses paging on all three endpoints and returns exactly the
    requested rows; `limit`/`offset` are ignored on that path.
  - Ordering is `title`/`name` (`COLLATE NOCASE`) followed by `id`. The `id`
    tiebreaker is a total order, so page boundaries through a tie group (many
    games called "Unknown Title", artists differing only by case) do not repeat
    or skip rows. The browser walks plain `OFFSET` pages; a scan still running
    between two requests can shift a boundary, which is why the client waits for
    the startup scan on its first read.
- **Unknown API paths:** any `/api/...` request that matches no route returns
  HTTP 404 with the JSON error body `{"error":"Not found"}` — for every method,
  including `POST`/`DELETE`. The bare `/api` path (zero trailing segments) is the
  same JSON 404; the rule matches the `/api` segment exactly, not a string
  prefix, so `/apiary` is an app-shaped route and still gets the SPA shell.
  Unknown paths never fall through to the SPA
  `index.html`. App routes (`/`, `/artists/42`, …) still get the SPA shell from
  the static fallback; the bundled `index.html` pins its relative
  favicon/splash/bundle URLs with `<base href="/">`, so a refresh on `/game/42`
  still loads `/chipbox.js` instead of `/game/chipbox.js`. Existing assets are
  served as themselves, never swallowed by the fallback. The browser decodes the
  error body and surfaces its
  message instead of reporting a JSON parse failure. The standard body is the
  shared `ErrorResponse` in `:cbox:common:models:api`.
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
  JDK 21 + Node 22/Yarn + pinned emsdk builds the bundle/WASM on `$BUILDPLATFORM`;
  the native stage builds the target-arch `libvgmstream.so` on `$TARGETPLATFORM`;
  a JRE 21 runtime runs non-root on glibc). Build with the `sage` submodule
  checked out:
  ```sh
  git submodule update --init --recursive
  docker build -t chipbox-server:dev .
  docker run --rm -p 127.0.0.1:8080:8080 \
    -v /path/to/music:/music:ro -v chipbox-data:/data chipbox-server:dev
  ```
  `/music` is read-only; `/data` persists the DB + work dir (a bind-mounted
  `/data` must be writable by uid `10001`).

  **Browser playback requires a secure context.** The web speaker is an
  `AudioWorklet`, which browsers expose only on HTTPS or `localhost`. Plain
  `http://<lan-host>:8080` still browses the library and caches tracks, but audio
  cannot start; the client shows an "Audio playback needs a secure connection"
  notification on Home. Terminate TLS in front of the container for LAN use, or
  tunnel to `http://localhost:8080` (`ssh -L 8080:127.0.0.1:8080`).

  Base images are pinned by manifest-list digest (`image:tag@sha256:…`, one pin
  for amd64 + arm64); the recorded digests and the bump procedure are in
  `docs/build.md` → "Pinned base images".

  **Multi-arch (both architectures, no registry).** The release job builds
  `linux/amd64` + `linux/arm64` in one buildx invocation and writes an OCI
  archive; `--load` only accepts a single platform, so the two-arch result cannot
  be loaded into the local daemon directly. Do the same locally:
  ```sh
  # One-time on an amd64 host: register arm64 binfmt so the arm64 native stage can
  # run under QEMU. Docker Desktop already provides this on macOS/Windows.
  docker run --privileged --rm tonistiigi/binfmt --install arm64
  docker buildx create --name chipbox --driver docker-container --use

  mkdir -p build
  docker buildx build --platform linux/amd64,linux/arm64 \
    --output type=oci,dest=build/chipbox-server-multiarch.tar \
    --cache-from type=local,src=.docker-cache \
    --cache-to type=local,dest=.docker-cache,mode=max .
  python3 scripts/ci/verify-oci-index.py \
    build/chipbox-server-multiarch.tar linux/amd64 linux/arm64
  ```
  The web/WASM stage runs once on the build host (`$BUILDPLATFORM`); only the
  small CMake vgmstream stage is per-arch, so the arm64 half does not run Gradle
  or emsdk under QEMU. For a runnable local image, build one platform with
  `--load` and smoke it (the script checks `/api/health`, the SPA, all nine
  WASM loader/WASM pairs, and the bundled native library):
  ```sh
  docker buildx build --platform linux/amd64 --load -t chipbox-server:dev .
  scripts/ci/web-image-smoke.sh chipbox-server:dev --platform linux/amd64
  # Inspect a cross-loaded arm64 image without QEMU (file contents only):
  docker buildx build --platform linux/arm64 --load -t chipbox-server:arm64 .
  scripts/ci/web-image-smoke.sh chipbox-server:arm64 --platform linux/arm64 --contents-only
  ```

  The image is built for amd64 on PRs and both amd64/arm64 on release. It is
  **not published to any registry**, has **no registry credentials or push
  step**, and there is **no public distribution** in v1 — the OCI tar is a
  local/release artifact only. There is **no authentication**: run it on
  loopback or a trusted LAN only. `scripts/ci/verify-docker-release.sh` statically
  checks the platform stages, both-arch release wiring, no-push rule, native-lib
  architecture assertion, pinned toolchain, and digest-pinned base images.

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
- Writes are atomic from a streaming client's point of view: new bytes go to
  `<member>.spc.tmp`, are flushed and closed, then moved over the final name
  (`ATOMIC_MOVE` where supported, a plain replace otherwise), so an open reader
  keeps its handle. A killed process can leave only a temp file, never a short
  member under a final name; the next extraction or prune pass removes stale
  temps. A failed move (a locked target on Windows) keeps the previous member
  and fails that archive for the scan; a later scan that re-reads the folder
  retries.
- One archive owns a content hash, recorded in a `.source` marker inside the
  hash directory. A second archive with byte-identical bytes is **refused**:
  its tracks are dropped, `tracksFailed` increments, and the server log names
  both archives. The scan still completes and the rest of the library is usable,
  but RSN pruning stays gated (`tracksFailed > 0`) until the duplicate is gone.
  The error repeats on each scan while both copies exist; remove one of the
  duplicate archives (duplicate rips, backup copies, `Album (1)` folders) and
  rescan. If the owning archive is renamed/removed, the
  marker's path no longer exists and the survivor takes ownership. If the
  surviving archive was the refused side, the new game adopts the existing
  extracted track rows and keeps their ids, so favorites/queues survive; the old
  game is pruned at the end of the scan.
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
atomic member writes (a repair does not break an open reader's handle, a failed
move keeps the previous member, stale temps are swept), the pruning gate, and
serving/path-leak via `/api/files/{id}` + `/api/tracks/{id}`. The duplicate-archive
suite (`RsnDuplicateTest`) runs on real Room/SQLite instead of `FakeDatabase`
(which cannot enforce the unique `(path, track_number)` index): it covers the
refusal, the health count/log line with no host path in JSON, a clean rescan
after removing the copy, owner-rename/owner-delete adoption with ids intact,
and duplicate detection through an injected filesystem.
`LibraryPagingTest` walks `limit`/`offset` pages over duplicate sort keys for
games, artists, and tracks, asserts the pages are the full catalog with no
duplicates, and covers the `ids` bypass plus the limit/offset coercion rules.
`UnknownApiRouteTest` covers the JSON 404 for bare `/api` and unknown
GET/POST/deep API paths, the exact-segment rule (`/apiary` is not an API path),
known routes not being shadowed, app deep paths still returning `index.html` (a
test-resource shell), and existing static assets such as `/chipbox.js` being
served as themselves rather than swallowed by the SPA fallback. `StaticRoutesTest`
covers the asset policy (a missing `/wasm/*` or extension-bearing path is a real
404, while app routes still fall back), and `WebIndexHtmlTest` reads the shipped
`apps/js/src/jsMain/resources/index.html` and asserts its `<base href="/">` (the
deep-link asset contract). Fixtures are
generated as stored-entry RAR4 archives in a test helper (no external `rar`
tool), and the scan uses `DatabaseRepository` over `FakeDatabase` with a no-op
vgmstream prober, so no native library is required.

DAO-level paging stability is covered by
`:cbox:common:database:real:jvmTest` (`DaoPagingStabilityTest`): Room over
bundled SQLite in-memory, with large tie groups, asserts the concatenated pages
are the unpaged list exactly once. `FakeDatabase` sorts stably and cannot
reproduce SQLite's unspecified order among peers, hence the real-SQLite layer.

## Entry point

`Main.kt`'s `main()` reads `ServerConfig.fromEnv`, builds the `ServerChipboxGraph`,
seeds the configured library dir into the persisted locations, launches a
background scan (the server serves immediately — endpoints return whatever is in
the DB now), then starts `embeddedServer(Netty, ...)` calling
[`configureChipboxServer`](src/main/java/net/sigmabeta/chipbox/server/ServerApplication.kt)
— content negotiation, status pages, partial content (Range), CORS, call
logging, and the `/api/health` + `libraryRoutes` + `fileRoutes` +
`apiNotFoundRoutes` + `staticRoutes` wiring. Route tests mount that same
function, so the tested stack is the shipped stack.

## Contents

| File | What it is |
| --- | --- |
| `Main.kt` | Entry point: config → graph → background scan → Ktor/Netty server. |
| `ServerApplication.kt` | `configureChipboxServer`: the plugin + routing stack shared by `main()` and the route tests (order: API routes, JSON API 404, SPA fallback). |
| `ServerConfig.kt` | Env-var-sourced runtime config; fails fast (exit 64) on a missing library dir. |
| `di/ServerChipboxGraph.kt` | `@DependencyGraph(AppScope)` root; accessors for repository, scanner, content sources, app scope. Factory takes `dbPath` + `workDir`. |
| `di/ServerModules.kt` | Binding containers composing the DB/scanner/repository/coroutines wiring. |
| `http/LibraryRoutes.kt`, `http/FileRoutes.kt`, `http/StaticRoutes.kt` | The `/api` JSON endpoints (paging + validation), file streaming (`/api/files/{trackId}`, chain files, covers/photos), and SPA-fallback static serving. The fallback serves `index.html` only for app-shaped routes; a missing asset (anything under `/wasm/` or with a filename extension) is a real 404, so a missing WASM module fires the loader's `<script>` error instead of parsing HTML as JavaScript. |
| `http/ApiNotFoundRoutes.kt` | Catch-all JSON 404 for unmatched `/api` paths, installed before the SPA fallback. |
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
