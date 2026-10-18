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
the server graph, an extractor writes those members to:

```text
<CHIPBOX_WORK_DIR>/rsn/<content-hash>/<member>.spc
```

Rules:

- Extraction is injected only on the server. Desktop, Android, and CLI keep the
  existing archive-path staging and are unaffected.
- The output directory is content-addressed and deterministic; the same archive
  content maps to the same paths.
- Orphaned extraction directories are removed only after a scan completes
  successfully. A failed or partial scan never deletes usable output.
- An extracted member becomes an ordinary track: `path` is the extracted `.spc`
  path, `extension` is `spc`, and `trackNumber` is `0`. The original archive
  basename and member index are used only while applying RSN m3u overlays.
- The extracted absolute path stays server-internal: `PublicUrls` rewrites
  `Track.path` to `/api/files/{id}` before the model reaches the browser.
- Track IDs re-key once on the first extraction. `CHIPBOX_WORK_DIR` is treated
  as installation-stable: moving it or deleting it invalidates browser-local
  track references. Client hydration drops unknown IDs instead of failing.
- A scanner version bump is required so existing RSN rows are rescanned.

## Browser state

All durable browser state is in `localStorage`; the server stores none of it.

| State | Storage | Notes |
| --- | --- | --- |
| Settings / debug settings | existing `LocalStorageStorage` | cross-tab via `storage` events |
| Favorites | `chipbox.favorites` | versioned JSON; ids + timestamps, newest first |
| Playlists | `chipbox.playlists` | versioned JSON; persisted next-id, ordered track ids |
| Play history | versioned JSON | bounded; safe fallback on corruption |
| Playback session | versioned JSON | queue, position, shuffle, repeat |

Every versioned store is one JSON object: `{ "schemaVersion": 1, "payload": … }`.
Favorites payloads hold `tracks` / `games` / `artists` lists of
`{id, favoritedAtMs}`; playlists hold `{nextId, playlists:[{id, name,
createdAtMs, trackIds}]}`. Missing data falls back to empty silently; corrupt,
older, or newer-schema data and quota/read failures fall back safely and surface
a non-fatal message (web shows it as a snackbar; the failure is also logged).

The implementation lives in `:cbox:common:browserstate` — a pure `commonMain`
codec plus `Storage`-backed repositories, with Node tests under `jsNodeTest`.
Reads derive from `Storage.savedStringFlow`, so write-through goes through
`LocalStorageStorage` and other tabs receive `storage` events. Writes are
last-writer-wins across tabs; there is no merge. Unknown track ids are dropped by
the UI at hydration time rather than stored as errors.

## Playback

- `RemoteRepository` returns library data; `HttpContentSource` fetches track
  bytes from `/api/files/{id}`.
- `WebAudioSpeaker` resamples to the `AudioContext` rate and feeds the
  `AudioWorklet` FIFO. The `AudioContext` must be created/resumed from the
  user's play gesture, not from the first audio buffer.
- The cache and staging file system is in-memory (`FakeFileSystem`); a reload
  re-renders tracks. There is no persistent PCM cache on web.
- The Web Media Session exposes play/pause/next/previous/seek and metadata.
- Emulation runs on the Kotlin/JS event loop; there is no Worker offload.

## Navigation and UI surface

- Voyager routes are mirrored to path-based URLs with `pushState`; the server's
  SPA fallback serves `index.html` for app routes. Back/Forward/refresh/deep
  links work.
- Browser Back/Forward are the source of truth for history; Escape maps to app
  back.
- The web UI hides Manage Library, Folder Picker, Rescan Status, and Clear
  Library. Settings and Home do not emit `PickFolder` on web.
- `AppInfo` uses the real build version and reports `isDebug = false`.

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
    modules are not configured.
  - `runtime`: JRE 21 on Ubuntu/glibc (the `.so` is glibc-linked, not alpine),
    non-root uid 10001, `/music` (read-only library) and `/data` (DB + work dir,
    persistent), `curl` healthcheck.
  ```sh
  git submodule update --init --recursive   # sage is a build input
  docker build -t chipbox-server:dev .
  docker run --rm -p 127.0.0.1:8080:8080 \
    -v /path/to/music:/music:ro -v chipbox-data:/data chipbox-server:dev
  ```
  A bind-mounted `/data` must be writable by uid 10001. Builds for
  `linux/amd64` on every PR and both architectures on release; images are **not
  pushed** to a registry yet.
- CI: the `web` job (after `static-analysis`) sets up JDK 21, Node 22 + the pinned
  Yarn, a cached pinned emsdk, and the Gradle cache; it builds WASM + the bundle +
  the server, runs `scripts/ci/verify-web-bundle.sh` and `jsNodeTest`, builds the
  amd64 image **without pushing**, and smokes `/api/health`, `/`, `/chipbox.js`,
  `/wasm/chipbox_gme.{js,wasm}` and the bundled native library. Locally,
  `scripts/ci/verify.sh web` mirrors it and `--web-image` adds the image smoke;
  both SKIP (not fail) when the toolchain/daemon is missing.

## Testing

- Shared pure logic (localStorage JSON codec/envelopes, URL ↔ route mapping) is
  Node-testable and runs in `jsNodeTest`. Modules that apply `sage.compose.kmp`
  disable their Node tests centrally in `settings.gradle.kts`: Compose's JS
  distribution ships Skiko only for browsers/WASM, so a Node import fails before
  a test can run. Pure modules (models, feature route keys, browser state) keep
  their `jsNodeTest`.
- The `/api/health` contract (`HealthStatus`, `ScanRetryPolicy`,
  `readListAwaitingScan`) lives in `cbox/common/models/api` with `commonTest`
  coverage, so the browser's retry behaviour is tested without a browser or a
  running server.
- Ktor server tests cover scan + RSN extraction, file serving, paging, and
  unknown-route handling. `apps/server`'s JVM tests cover the
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
