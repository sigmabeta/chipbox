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
| Favorites | versioned JSON | ids + timestamps, newest first |
| Playlists | versioned JSON | persisted next-id, ordered track ids |
| Play history | versioned JSON | bounded; safe fallback on corruption |
| Playback session | versioned JSON | queue, position, shuffle, repeat |

All stores use a versioned envelope, tolerate unknown/missing track IDs, and
surface quota errors as a non-fatal message. Write-through goes through
`LocalStorageStorage` so other tabs receive `storage` events.

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

- `apps/js` is gated behind `-Psage.js=true`. Webpack tasks need
  `--no-parallel --no-configuration-cache`.
- The bundle build includes all nine WASM emulator outputs; they are not
  committed. Local builds need `$EMSDK` or `-Psage.js.emsdk=...`, plus CMake.
- `apps/server` bundles the production JS for `installDist`/`distZip`/`distTar`.
- The Docker image is multi-stage: a build stage with JDK 21, Node 22/Yarn,
  emsdk, and CMake produces the bundle, WASM, and native vgmstream library; a
  JRE 21 runtime stage runs the server.
- CI builds the image for `linux/amd64` on every PR and both architectures on
  release. Images are **not pushed** to a registry yet.

## Testing

- Shared pure logic (localStorage JSON codec/envelopes, URL ↔ route mapping) is
  Node-testable and runs in `jsNodeTest`.
- Ktor server tests cover scan + RSN extraction, file serving, paging, and
  unknown-route handling.
- No Playwright or other browser automation. UI and audio are verified manually.
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
