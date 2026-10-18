# Web app

The browser app is a client for a self-hosted `chipbox-server`. It is not a
hosted service and it is not a serverless player. The architecture decisions and
their rationale live in [ADR-0003](../../docs/adr/0003-web-app-self-hosted-browser-client.md);
this document is the working map and the v1 acceptance gate.

> **Status:** the decisions are accepted; the code does not implement them all
> yet. Where the current tree falls short, the checklist at the bottom is the
> gap list. Do not treat an unchecked item as already shipped.

## Topology

```text
browser (apps/js)                         chipbox-server (apps/server)
┌─────────────────────────────┐           ┌──────────────────────────────┐
│ Compose UI + Voyager        │  JSON     │ Ktor/Netty                   │
│ RemoteRepository ───────────┼──────────▶│ library + file + SPA routes  │
│ HttpContentSource ──────────┼──────────▶│ LocalFileContentSource       │
│ WASM emulators + Web Audio  │  bytes    │ scanner + Room + work dir    │
│ localStorage state          │           │ startup scan                 │
└─────────────────────────────┘           └──────────────────────────────┘
```

The browser and the server share the Compose screens, the repository/model
types, the playback chain, and the emulator wrappers. Platform differences live
in `apps/js` (browser services, WASM bindings, localStorage) and `apps/server`
(headless scan, HTTP routes, static bundle).

## Library lifecycle

- The library root is `CHIPBOX_LIBRARY_DIR`; the work dir is
  `CHIPBOX_WORK_DIR` (default `./.chipbox-server`); the DB is
  `CHIPBOX_DB_PATH` (default `<work dir>/library.sqlite`).
- The server scans on every start, in the background. It serves immediately;
  endpoints return the current DB contents while the scan runs.
- `GET /api/health` reports the live scan state
  (`unknown|idle|scanning|complete|failed`), elapsed time, found/failed counters,
  and a failed file's basename. `ok` is false only for a failed scan. The body
  never contains the host library path. The browser retries an empty first
  library read while the scan is running under a bounded policy, so a client
  opened during the scan shows the library when it finishes instead of staying
  empty; the wait always ends at the attempt cap.
- Unchanged folders are skipped by folder signature plus scanner/reader
  versions. New and changed files are picked up on restart. Removed tracks are
  reconciled and removed folders are pruned at the end of a successful scan.
- There is no rescan endpoint, file watcher, or periodic scan. Restarting the
  server is the supported way to refresh the library.
- Folder management and scan progress are not part of the web UI. They are
  admin tasks (`CHIPBOX_LIBRARY_DIR`, restart).

## RSN extraction

The scanner already decodes RSN archives once to read per-member SPC tags. In
the server graph, an injected `RsnExtractor` writes those members to:

```text
<CHIPBOX_WORK_DIR>/rsn/<content-hash>/<member>.spc
```

Rules:

- Extraction is injected only on the server (`ServerRsnExtractor`). Desktop,
  Android, and CLI keep the existing archive-path staging and are unaffected;
  the collaborator defaults to null at every other construction site.
- The output directory is content-addressed and deterministic; the same archive
  content maps to the same paths. Member names are sanitized (directory
  components stripped, unsafe characters replaced), and duplicate basenames
  within one archive get a zero-padded member-index prefix. A member whose
  existing bytes match is reused rather than rewritten.
- Extraction happens at scan time, after the RSN m3u overlay and filename-title
  fallback, and before persistence. This ordering is load-bearing: the overlay
  matches on the original archive basename + 1-based member index. An extracted
  member then becomes an ordinary track: `path` is the extracted `.spc` path,
  `extension` is `spc`, and `trackNumber` is `0`.
- Orphaned extraction directories are removed only after a scan completes with
  `tracksFailed == 0`, and only after removed folders are pruned. A directory is
  kept when any surviving track path is under it (this includes unchanged
  folders the scan skipped). A failed or partial scan never deletes usable
  output; individual delete failures are tolerated and retried next time.
- The extracted absolute path stays server-internal: `PublicUrls` rewrites
  `Track.path` to `/api/files/{id}` before the model reaches the browser.
  Serving needs no new endpoint — `LocalFileContentSource` reads the extracted
  path through the same `/api/files/{id}` route.
- Track IDs re-key once on the first extraction. `CHIPBOX_WORK_DIR` is treated
  as installation-stable: moving it or deleting its contents invalidates
  browser-local track references (the DB's folder signature does not notice),
  and client hydration drops unknown IDs instead of failing.
- Scanner version 2 forces existing RSN rows to be re-read once on upgrade.
  Rows persisted after extraction are `spc` tracks, so future RSN fixes need
  another scanner bump (or a dedicated stored extraction version).

## Browser state

All durable browser state is in `localStorage`; the server stores none of it.

| State | Storage | Notes |
| --- | --- | --- |
| Settings / debug settings | existing `LocalStorageStorage` | cross-tab via `storage` events |
| Favorites | `chipbox.favorites` | versioned JSON; ids + timestamps, newest first |
| Playlists | `chipbox.playlists` | versioned JSON; persisted next-id, ordered track ids |
| Play history | `chipbox.playback_history` | versioned JSON; newest 500 plays; legacy raw array read as v0 |
| Playback session | `playback.session.snapshot` | versioned JSON; queue, current track, position, shuffle, repeat; legacy raw snapshot read as v0 |

Every versioned store is one JSON object: `{ "schemaVersion": 1, "payload": … }`.
Favorites payloads hold `tracks` / `games` / `artists` lists of
`{id, favoritedAtMs}`; playlists hold `{nextId, playlists:[{id, name,
createdAtMs, trackIds}]}`. Missing data falls back to empty silently; corrupt,
older, or newer-schema data and quota/read failures fall back safely and surface
a non-fatal message (web shows it as a snackbar; the failure is also logged).

The implementation lives in `:cbox:common:browserstate` — a pure `commonMain`
codec plus `Storage`-backed stores, with Node tests under `jsNodeTest`. Reads
derive from `Storage.savedStringFlow`, so write-through goes through
`LocalStorageStorage` and other tabs receive `storage` events. Writes are
last-writer-wins across tabs; there is no merge. Unknown track ids are dropped by
the UI at hydration time rather than stored as errors.

**Play history.** The log records only qualifying plays (10 seconds listened, or
played to the end) and is capped at 500 entries. Recording and loading both trim
the oldest entries by timestamp, so an oversized or out-of-order blob from an
older build is repaired on the next read/write. The pre-versioning web app stored
a bare JSON array at the same key; that array is read as schema version 0 and
rewritten inside the envelope on the next play. The Home recently-/most-played
rows are folds over this log (distinct tracks newest-first; counts above one,
ordered by count then recency), so they survive a reload unchanged.

**Playback session.** On launch the web host starts the shared
`RealPlaybackSessionPersister` over the versioned store: queue, current track,
position, shuffle, and repeat come back **paused** at the saved offset. Before
`SessionRequest.Restore`, the snapshot's track ids are resolved through
`Repository.getTracksByIds`; unknown ids are dropped, the current track is
remapped to the nearest surviving queue position, and the snapshot is cleared if
nothing survives. A failed lookup leaves the snapshot untouched (a transient
server error must not erase a resumable session). Saving happens on the
`PLAYING → PAUSED` edge, and `snapshotNow()` also runs on `pagehide`,
`beforeunload`, and `visibilitychange=hidden`, so reopening a tab after playing
without pausing keeps the position. The visibility flush is a non-teardown save
(the page stays alive), so a later real stop still clears; `pagehide`/`beforeunload`
use the teardown form so a released player cannot wipe the snapshot they just
wrote. A real stop clears the snapshot, and a late teardown flush cannot
resurrect it.

**Multi-tab.** History (and favorites/playlists) are cross-tab-live because their
reads derive from `savedStringFlow`. The playback session is not merged: writes
are last-writer-wins, and restore is launch-only. A second tab does not adopt a
session started after it loaded; whichever tab pauses or hides last owns the
saved snapshot, and reloading a tab restores that snapshot.

## Playback

- `RemoteRepository` returns library data; `HttpContentSource` fetches track
  bytes from `/api/files/{id}`.
- `WebAudioSpeaker` resamples to the `AudioContext` rate and feeds the
  `AudioWorklet` FIFO. The `AudioContext` must be created/resumed from the
  user's play gesture, not from the first audio buffer: `RealDirector.request`
  calls `Speaker.prepareForPlayback()` synchronously for Start/Play/PlayPosition,
  before any coroutine work, and the web sink creates + resumes the context
  there (required by Safari).
- Worklet initialization is the shared pure `AudioInitState` reducer
  (`Idle → Preparing → Ready`/`Failed`, 4 s per attempt, at most one retry).
  `BaseSpeaker.awaitSinkReady()` gates the consume loop before it dequeues a
  buffer: frames produced while the module loads are held by producer
  backpressure (not dropped), and a terminal init failure exits the loop after
  emitting `SpeakerEvent.Error`. `teardown()` flushes the FIFO but keeps the
  node and context alive, so a play after end-of-setlist/stop is not mute; a
  failed init is reset on stop so the next gesture can rebuild it.
- A worklet/node/context failure and a stalled or suspended context are visible:
  the speaker emits `SpeakerEvent.Error`; the director moves to `ERROR` (also
  for a failed last track, instead of looking like a normal stop); and the web
  host routes `Director.errorEvents()` into the app snackbar while NowPlaying
  keeps its error log.
- The cache and staging file system is in-memory (`FakeFileSystem`); a reload
  re-renders tracks. There is no persistent PCM cache on web.
- The Web Media Session exposes play/pause/next/previous/seek and metadata.
  Handler registration, metadata writes, and position writes are wrapped in
  `runCatching`: an unsupported action (Safari `NotSupportedError`) must not
  abort app startup or kill the playback collector.
- Emulation runs on the Kotlin/JS event loop; there is no Worker offload.

## Navigation and UI surface

- Voyager routes are mirrored to path-based URLs with `pushState`; the server's
  SPA fallback serves `index.html` for app routes. Back/Forward/refresh/deep
  links work.
- The mapping is pure and lives in `:cbox:common:webroutes` (`RouteUrl.encode` /
  `RouteUrl.decode`, `ChipboxTarget.Tab` vs `.Deep`). It has no Compose/Voyager
  imports, so it is Node-tested. Its URL table is documented in that module's
  README. Only web-visible routes exist: admin destinations (Manage Library,
  Folder Picker, Rescan Status) and debug-only destinations (Playback Status,
  Crash Log, Component Library) do not encode, and their paths decode as unknown.
  Picker-mode `Playlists` is never encoded; it maps to `/library/playlists`.
  `/settings/errors` is the Error Log and is gated by `showErrorLog`.
- `apps/js`'s `BrowserHistoryNavigation` owns the `window.history` side. Every
  in-app deep push and tab switch pushes an entry; an in-app pop that the browser
  did not drive (no app entry behind the current one — a fresh deep link or
  refresh) replaces the current entry. `popstate` decodes the new URL and either
  selects the tab or pops/pushes the active tab's Navigator. Initial load applies
  the URL once the navigators exist and canonicalises it with `replaceState`
  (unknown path → `/`).
- Browser Back/Forward are the source of truth for history. Escape, the up arrow,
  and device back run through the same back handler: when the entry has an app
  entry behind it, the host calls `history.back()` and `popstate` applies the pop;
  at the first app entry the shell pops natively and replaces the URL.
- Refresh/deep links reconstruct detail screens from their typed arguments
  (assisted VMs refetch by id), so the copied URL lands on the same screen.
  Now Playing depends on the restored playback session (ticket 07).
- The SPA fallback never answers an API request. Any unmatched `/api/...` path
  returns HTTP 404 with the standard `{"error": …}` JSON body (every method), so
  the client can show the server's message instead of failing to decode
  `index.html`. Paged list endpoints (`games`, `artists`, `tracks`) clamp
  `limit` into `1..500`, default `offset` to 0, and order by their display key
  plus `id`, so pages partition the catalog under ties.
- The fallback serves `index.html` only for app-shaped routes. A missing asset
  (anything under `/wasm/` or with a filename extension) is a real 404, so the
  injected WASM `<script>` fires its `error` listener instead of parsing HTML as
  JavaScript. Unknown `/api/*` paths are the API routes' JSON 404 and must stay
  ahead of this fallback.
- The bundled `index.html` declares `<base href="/">`; without it a refresh on
  `/game/42` resolves `chipbox.js` as `/game/chipbox.js` and loads a blank page.
  Existing static assets are served as themselves, not through the SPA default.
- The web UI hides Manage Library, Folder Picker, Rescan Status, and Clear
  Library. Settings and Home do not emit `PickFolder` on web.
- Host policy is an explicit DI value, not `expect`/`actual`: every app graph
  binds `AppCapabilities(canManageLibrary, showDebugSection, showErrorLog)`
  (defaults `true`). The web graph binds the first two false and keeps
  `showErrorLog` true. Screens seed those flags into their list state, and the
  shell's event sinks refuse blocked destinations as a final guard (a stale row,
  synthetic event, or deep link cannot open the blank picker; a stray
  `PickFolder` shows a "managed on the server" snackbar). The persisted five-tap
  debug toggle cannot reveal the debug section when `showDebugSection` is false.
- The Error Log stays reachable on web as a **Diagnostics** row in Settings
  (outside the debug section) because it is browser/session-local. Playback
  Status, Crash Log, and Component Library are debug-only and therefore hidden.
- `AppInfo` uses the real build version (generated from git at build time) and
  reports `isDebug = false`; the browser title and splash use release assets.

## Build and release

- `apps/js` is gated behind `-Psage.js=true`. Every web invocation also needs
  `-Dorg.gradle.isolated-projects=false -Dorg.gradle.configureondemand=false
  --no-parallel` (Isolated Projects cannot be combined with the Kotlin/JS npm
  model, and `--no-configuration-cache` is impossible with Isolated Projects
  off). The configuration cache stays enabled.
- Toolchain is pinned in `docker/`: Node `22.14.0` (`node-version`), Yarn
  `1.22.22` (`yarn-version`), emsdk `4.0.7` (`emsdk-version`); CI and the image
  read the same files. A system Node/Yarn is mandatory (Kotlin's downloads are
  disabled). `gradle/wrapper` is the Gradle version. Base images are floating tags
  (digest pinning is a follow-up) — reproducibility means the pinned toolchain +
  caches, not bit-identical images.
- The bundle build includes all nine WASM emulator outputs; they are not
  committed. `jsProcessResources` depends on `copyEmulatorWasm`, so every bundle
  path (production bundle, dev server, `installDist`) ships them. `checkEmsdk`
  fails at execution when `$EMSDK`/`-Psage.js.emsdk` is missing or not activated,
  instead of silently producing an emulator-less bundle. The list lives in
  `apps/js/wasm-emulators.txt`.
- Build and verify locally:
  ```sh
  export EMSDK="$HOME/emsdk"   # or -Psage.js.emsdk=/path
  ./gradlew :apps:js:verifyWebBundle :apps:server:installDist \
    -Psage.js=true -Dorg.gradle.isolated-projects=false \
    -Dorg.gradle.configureondemand=false --no-parallel
  scripts/ci/verify-web-bundle.sh   # standalone: 9 pairs + index.html + chipbox.js
  ```
- `apps/server` bundles the production JS for `installDist`/`distZip`/`distTar`
  (requires `-Psage.js=true`). The native `libvgmstream` used by the scanner is
  built by CMake (`:apps:jvm:buildHostNativeVgmstream`) and consumed via
  apps/jvm's `hostVgmstreamElements`; the distributions copy it into
  `lib/native/` and point `java.library.path` there. No committed binaries.
- The Docker image (`/Dockerfile`, `/.dockerignore`) is multi-stage:
  - `web` (BUILDPLATFORM): JDK 21, Node 22 + Yarn, pinned emsdk, CMake → WASM,
    bundle, bundle assertion, and the server distribution (`-Pchipbox.skipNative`,
    so no Android SDK is needed).
  - `native` (TARGETPLATFORM): JDK 21 + CMake → `libvgmstream.so` for the target
    architecture, configured with `--configure-on-demand` so the Android native
    modules are not configured. The stage asserts the produced ELF
    `e_machine` matches `$TARGETARCH` (amd64 `3e 00`, arm64 `b7 00`).
  - `runtime` (TARGETPLATFORM): JRE 21 on Ubuntu/glibc (the `.so` is glibc-linked,
    not alpine), non-root uid 10001, `/music` (read-only library) and `/data` (DB
    + work dir, persistent), `curl` healthcheck. It also asserts at build time
    that the packaged server jar carries `index.html` and all nine WASM
    loader/WASM pairs (the list is `apps/js/wasm-emulators.txt`).
  ```sh
  git submodule update --init --recursive   # sage is a build input
  docker build -t chipbox-server:dev .
  docker run --rm -p 127.0.0.1:8080:8080 \
    -v /path/to/music:/music:ro -v chipbox-data:/data chipbox-server:dev
  ```
  A bind-mounted `/data` must be writable by uid 10001. Builds for
  `linux/amd64` on every PR and both architectures on release; images are **not
  pushed** to a registry yet. A two-arch build needs QEMU/binfmt on an amd64
  host and an OCI archive output (`--load` is single-platform):
  ```sh
  docker run --privileged --rm tonistiigi/binfmt --install arm64
  mkdir -p build
  docker buildx build --platform linux/amd64,linux/arm64 \
    --output type=oci,dest=build/chipbox-server-multiarch.tar \
    --cache-from type=local,src=.docker-cache \
    --cache-to type=local,dest=.docker-cache,mode=max .
  scripts/ci/verify-oci-index.py build/chipbox-server-multiarch.tar linux/amd64 linux/arm64
  ```
  The release job mirrors this with `type=gha` caching, uploads the OCI archive
  as a workflow artifact (no registry, no credentials), smokes the loaded amd64
  image, and inspects the cross-loaded arm64 image's contents without QEMU.
  `scripts/ci/verify-docker-release.sh` statically pins the platform stages,
  both-arch release wiring, no-push rule, and native-lib architecture check.
- CI: the `web` job (after `static-analysis`) sets up JDK 21, Node 22 + the pinned
  Yarn, a cached pinned emsdk, and the Gradle cache; it builds WASM + the bundle +
  the server, runs `scripts/ci/verify-web-bundle.sh` and `jsNodeTest`, builds the
  amd64 image **without pushing**, and smokes `/api/health`, `/`, `/chipbox.js`,
  all nine `/wasm` loader/WASM pairs, and the bundled native library via
  `scripts/ci/web-image-smoke.sh`. `static-analysis` also runs
  `scripts/ci/verify-docker-release.sh` (platform stages, release wiring, no push,
  native-lib arch, pinned toolchain). Locally, `scripts/ci/verify.sh web` mirrors
  the bundle/tests and `--web-image` adds the image build + smoke; both SKIP (not
  fail) when the toolchain/daemon is missing.

## Testing

- Shared pure logic (localStorage JSON codec/envelopes, URL ↔ route mapping) is
  Node-testable and runs in `jsNodeTest`. Modules that apply `sage.compose.kmp`
  disable their Node tests centrally in `settings.gradle.kts`: Compose's JS
  distribution ships Skiko only for browsers/WASM, so a Node import fails before
  a test can run. Pure modules (models, feature route keys, browser state, the
  `:cbox:common:webroutes` codec) keep their `jsNodeTest`.
- The `/api/health` contract (`HealthStatus`, `ScanRetryPolicy`,
  `readListAwaitingScan`) lives in `cbox/common/models/api` with `commonTest`
  coverage, so the browser's retry behaviour is tested without a browser or a
  running server.
- Ktor server tests cover scan + RSN extraction, file serving, paging,
  unknown-route handling (JSON `/api` 404s, deep app paths serving the SPA shell,
  and the static-asset 404 policy: a missing `/wasm/*` is never SPA HTML while
  app routes still fall back), and the shipped `index.html`'s `<base href="/">`.
  `apps/server`'s JVM tests cover the
  `ScannerState` → `HealthStatus` mapping (including the no-path-leak rule) and
  the loopback-bind predicate.
- No Playwright or other browser automation. UI and audio are verified manually.
- `scripts/ci/verify.sh web` mirrors the CI web job and SKIPs cleanly when
  emsdk/node/yarn are unavailable; `--web-image` adds the Docker build + smoke.
- `scripts/ci/verify.sh` and the PR `web` job must pass before a push.

## v1 acceptance checklist

Run through this on a clean Docker build and in current desktop Chrome, Firefox,
and Safari. A failure blocks "usable".

**Install and scan**

- [ ] Image builds and starts with `-v /path/to/music:/music:ro` and a persistent
      `/data` volume.
- [ ] On first start the scan populates the library; `/api/health` reports the
      scan state.
- [ ] Adding files and restarting picks them up; removing files and restarting
      removes them.
- [ ] A server restart does not re-scan unchanged folders unnecessarily.

**Browse and search**

- [ ] Home, games, artists, platforms, all tracks, and search return results.
- [ ] Game/artist paging returns distinct pages, with no duplicates or skipped
      entries.
- [ ] Covers and artist photos load.
- [ ] Unknown `/api/*` paths return a JSON 404, not the SPA HTML.

**Playback**

- [ ] One track from each emulator family plays: gme, vgm, psf, ssf, usf, ncsf,
      2sf, gba, vgmstream.
- [ ] RSN members extracted at scan time play without a browser unrar step.
- [ ] Play/pause, seek, next/previous, shuffle, and repeat work.
- [ ] The queue advances correctly across formats.
- [ ] Media Session metadata and controls work where the browser exposes them.
- [ ] No console errors; a missing WASM module fails visibly.

**Persistence**

- [ ] Reload preserves favorites, playlists, play history, queue, position,
      shuffle/repeat, theme, and volume/resampler settings.
- [ ] Two tabs see each other's favorites/playlists/history changes.
- [ ] Clearing site data resets state without breaking the app.
- [ ] A missing/unknown track ID is dropped from favorites/history silently.
- [ ] A localStorage quota failure shows a non-fatal message.

**Navigation**

- [ ] In-app Back, browser Back/Forward, refresh, and a copied deep link all
      land on the same screen.
- [ ] Escape goes back.
- [ ] Unknown app paths fall back to the root screen.

**Web-only surface**

- [ ] Manage Library, Folder Picker, Rescan Status, and Clear Library are not
      reachable.
- [ ] App info shows the real version and is not marked debug.
- [ ] The app works without any auth environment configured on a trusted LAN.

## Out of scope for v1

- Authentication and authorization (a **hard gate before distribution**).
- Multi-user hosting, uploads, per-user libraries.
- Server-side rendering/transcoding.
- File watcher or periodic rescans.
- PWA, service worker, offline playback.
- Mobile browser gating.
- Playwright or other browser automation.
