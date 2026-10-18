# Web app is a self-hosted browser client with client-side emulation

The browser groundwork (`apps/js`, `apps/server`) predates a product decision.
It already contains a Compose UI shell, nine Emscripten emulator bindings, a Web
Audio speaker, and a Ktor server that scans and serves a library — but it has no
CI or packaging path, its library lifecycle is stubbed, its browser state is
ephemeral, and several playback paths are broken. "Usable web app" could mean a
hosted multi-user service, a serverless client that reads a local folder, or a
single-user server the user hosts themselves. We choose the last.

## Decision

- **Topology:** one `chipbox-server` per installation, owning one library
  directory, plus a browser client. No accounts, uploads, multi-tenancy, or
  per-user separation. The server is configured by environment variables.
- **Playback:** the browser runs the emulators (WASM) and Web Audio; the server
  only streams original file bytes. There is no server-side rendering or
  transcoding.
- **State:** favorites, playlists, play history, playback session/queue, and
  settings live in browser `localStorage`. State is per-browser, not shared
  across devices, and is lost when site data is cleared.
- **Library lifecycle:** the server scans `CHIPBOX_LIBRARY_DIR` on startup.
  Adding or removing music requires restarting the server. Folder management,
  rescan, and scan progress are admin concerns and are not exposed in the web UI.
- **RSN:** extracted server-side at scan time, never in the browser. Members are
  written to a content-addressed directory under `CHIPBOX_WORK_DIR`, are pruned
  only after a successful scan, and become ordinary `spc` tracks. Extraction is
  a server-only scanner collaborator; desktop, Android, and CLI keep archive
  staging. The extracted path becomes the track's `path` and the track number is
  reset to zero, so RSN track IDs re-key once on the first extraction. Because
  the output is content-addressed, only one archive may own a given content hash
  per scan; a byte-identical duplicate in another folder is refused as a per-file
  scan failure and the admin removes one copy.
- **Navigation:** Voyager routes are mirrored into browser URLs with
  `pushState`, so Back, Forward, refresh, and deep links work.
- **Web-only UI:** Manage Library, Folder Picker, Rescan Status, and Clear
  Library are hidden on web. `AppInfo` reports the real build version with
  `isDebug = false`.
- **Packaging:** a multi-stage Docker image bundles the server, the JS bundle,
  all WASM emulators, and the native vgmstream library. Images build for
  `linux/amd64` and `linux/arm64` but are not published yet. `distZip` remains a
  secondary artifact.
- **"Published"** means distributed outside the repository: pushed to a registry,
  attached to a public release, or otherwise made downloadable. CI-internal logs,
  caches, and ephemeral workflow artifacts are not publication. v1 publishes
  nothing; `push` stays false and no release asset carries the image.
- **Authentication:** there is no authentication in this version. The server is
  for loopback or trusted-LAN use only. **No public image, registry push, or
  internet-facing deployment may happen until an authentication story exists**
  (shared token or reverse-proxy auth). The server warns when it binds a
  non-loopback address.
- **Testing:** a web CI job builds WASM with a pinned emsdk, builds the bundle
  and server, runs Node-testable shared logic and Ktor server tests, and builds
  an amd64 Docker image. There is no Playwright/browser automation; UI and audio
  are gated by a written manual checklist.
- **Browser support:** current desktop Chrome, Firefox, and Safari are gated.
  Mobile browsers are best-effort. There is no PWA or offline mode.

## Consequences

The client keeps the expensive emulation work out of the server and reuses the
existing WASM groundwork. The cost is that emulation and resampling compete with
the UI event loop, the server cannot serve a client that lacks WebAssembly, and
the browser must download the emulator modules.

Browser-local state keeps the server stateless and simple, at the price of no
cross-device sync and a hard dependency on track-ID stability. Because RSN
extraction re-keys tracks by extracted path, changing `CHIPBOX_WORK_DIR` or
clearing the work directory invalidates saved favorites, playlists, history, and
queues. Hydration must tolerate unknown track IDs rather than fail.

Restart-only scanning avoids a file-watcher matrix (especially on network
mounts) but makes library updates a deliberate admin action.

Building WASM in CI makes the web app reproducible and prevents the current
class of silent packaging drift, at the cost of a slow job that needs emsdk and
CMake caching. Skipping browser automation leaves the audio path to the manual
checklist; that is an accepted v1 trade-off, not a permanent decision.

## References

- `apps/js/README.md` — the browser client and its run/build commands.
- `apps/server/README.md` — the library server, configuration, and web bundle.
- `arch-docs/architecture/web-app.md` — topology, lifecycle, and the v1
  acceptance checklist.
