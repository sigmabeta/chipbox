# CI migration: CircleCI → GitHub Actions

Status: **cut over** (branch `ci-github-actions`). CircleCI is deleted; GitHub
Actions is the only CI — `ci.yml` (push/PR, every branch) and `release.yml` (tags).
Remaining: native installers (Phase 2b) and the macOS target (Phase 3). The
parity `release.yml` is authored but not yet exercised on a real tag.

## Why

GitHub Actions gives this **public** repo free `windows-latest` and `macos-latest`
runners. That unblocks what CircleCI couldn't do cheaply: native desktop
installers on every OS (`jpackage` can't cross-compile), the **macOS desktop**
build, and — infra-side — eventual **iOS** (Actions Macs ship Xcode). Goal: one CI
system, more platforms, no second migration later.

## End state

- One CI system: GitHub Actions. `.circleci/config.yml` deleted.
- Every current check preserved; **Windows + macOS native installers** added.
- **macOS desktop** target built and shipped (unsigned `.dmg` for now — signing/
  notarization is a TODO).
- **Windows builds natively** on `windows-latest`; the MinGW cross-compile is
  retired (it proved the vendored CMakeLists are Windows-ready, which the native
  build still relies on).
- iOS infra-ready: adding it later is a new job on the macOS runners, not a switch.

## Carries over unchanged

- **Remote Gradle build cache** (`gradle.sebacloud.org`): HTTP, anonymous read.
  Push needs `CI=true` (Actions sets it) + `GRADLE_CACHE_USER`/`GRADLE_CACHE_PASSWORD`.
- All Gradle logic: APK splits, native build-logic, `chipbox.jks` (committed),
  appVersioning tag derivation.

## Job mapping

| CircleCI | GitHub Actions | Notes |
|---|---|---|
| `setup` + persisted workspace | dropped; each job `actions/checkout` (`submodules: recursive`, `lfs: true`, `fetch-depth: 0`) + `gradle/actions/setup-gradle` | no shared workspace; build cache covers reuse |
| `static-analysis` | `lint` | ktlint + detekt, `-Pchipbox.skipNative` |
| `unit-test-jvm` | `unit-test` | |
| `screenshot-test` | `screenshot` | Paparazzi; needs LFS goldens |
| `shared-build` | `shared-build` | |
| `android-lint` | `android-lint` | NDK `29.0.14206865` via sdkmanager if absent |
| `build-release-jvm` | `desktop-linux` | + `.deb`/`.rpm` (release only) |
| `build-windows-jvm` (cross) | `desktop-windows` on `windows-latest` | native build; retires cross-compile |
| `build-release-apk` | `android-apk` | split signed APKs |
| `publish-release` | `release` | built-in `GITHUB_TOKEN` — no PAT |

Inter-job files: `actions/upload-artifact` / `download-artifact` (replaces
`persist_to_workspace`).

## Workflows

- `ci.yml` — push / pull_request: lint, unit-test, screenshot, shared-build,
  android-lint, desktop-linux, android-apk. **(Phase 1, done.)** Windows is not
  in `ci.yml` — it builds at release time only.
- `release.yml` — tag `^\d+\.\d+.*`: **Phase 2a (done)** ports the CircleCI
  release as-is — Linux distZip (native host), Windows distZip (MinGW
  cross-compile on ubuntu), split signed APKs → publish a GitHub Release via the
  built-in `GITHUB_TOKEN`. **Phase 2b (pending)** adds native installers
  (`.deb`/`.rpm`/`.msi`/`.dmg`) — see below; it needs build-logic that doesn't
  exist yet.
- `pages.yml` — already exists, unchanged.

> **Native-host build reality (verified):** the desktop native build supports
> only `LINUX` (native to the build machine) and `WINDOWS_X64` (MinGW-w64
> cross-compile from Linux) — `NativeEmulators.NativeHostTarget`. There is **no
> native Windows-host path and no macOS/`darwin` target**, and `apps/jvm` has no
> `nativeDistributions` block. So "Windows: go native on `windows-latest`",
> macOS `.dmg`, and any jpackage installer are all **blocked on new build-logic**,
> not just new CI YAML. Phase 2a therefore keeps the proven MinGW cross-compile.

> **Untested-on-tag caveat:** `release.yml` is now the sole release path (CircleCI
> deleted), but it has not yet run on a real tag — the next version tag is its live
> test. Its publish step is gated to tag refs, so a `workflow_dispatch` run only
> exercises the build jobs (no Release is created).

## Native installers

`jpackage` per OS, each on its native runner. The real work: **bundle the
hand-built emulator natives into the jpackage app image + wire
`java.library.path`** (`nativeDistributions` doesn't know about our
`.so`/`.dll`/`.dylib`). Validate a `.deb` locally before trusting CI.

macOS `.dmg` ships **unsigned** for now → Gatekeeper warns. TODO: Apple Developer
ID signing + notarization.

## macOS desktop target

Generalize the native "host" build to detect the host OS (Linux→`linux`/`.so`,
macOS→`darwin`/`.dylib`) instead of hardcoding Linux; build natively with clang on
`macos-latest`.

## iOS-readiness (no second switch)

macOS runners already have Xcode — iOS's only hard infra need. Adding iOS later =
a new job + a Kotlin/Native target. The real work (cinterop bindings to replace
the JNI bridge for the 9 cores, CoreAudio `actual`s) is a separate future project;
**nothing in this migration needs redoing for it.**

## Secrets to add in GitHub

`CHIPBOX_KEY_ALIAS`, `CHIPBOX_KEYSTORE_PASSWORD`, `CHIPBOX_KEY_PASSWORD` (APK
signing); `GRADLE_CACHE_USER`, `GRADLE_CACHE_PASSWORD` (build-cache push).
`GH_TOKEN` not needed — releases use the built-in `GITHUB_TOKEN`.

## Phased rollout

1. **Parity** — `ci.yml` runs alongside CircleCI on `ci-github-actions`; confirm
   every check matches green. CircleCI stays. **(Done.)**
2. **Release** — split into:
   - **2a Parity** — `release.yml` ports the CircleCI release (distZips + split
     APKs + publish via `GITHUB_TOKEN`). No new build-logic. **(Done; untested on
     a real tag — see dual-release caveat.)**
   - **2b Native installers** — add `apps/jvm` `nativeDistributions` (`.deb`/
     `.rpm` on ubuntu, `.msi` on `windows-latest`, `.dmg` on `macos-latest`) +
     bundle the JNI natives into the jpackage image. Needs a native Windows-host
     build path (`.msi` can't cross-compile) and overlaps Phase 3 for macOS.
3. **macOS target** — generalize the native host build to detect macOS
   (`darwin`/`.dylib`) + clang on `macos-latest`; then the `.dmg` leg of 2b.
4. **Cut over** — **(Done.)** Deleted `.circleci/config.yml`; broadened `ci.yml`
   to every branch; migrated the `CIRCLE_BRANCH` versionCode factor to
   `GITHUB_REF_NAME` (`apps/android/build.gradle.kts`); updated `Readme.md`,
   `scripts/verify.sh`, `settings.gradle.kts` comments. Secrets already live as
   GitHub Actions secrets (`CHIPBOX_*`, `GRADLE_CACHE_*`); releases use the
   built-in `GITHUB_TOKEN` (no PAT).
5. **(future)** iOS.

## Toolchain facts

- JDK **21** (Temurin) — `build-logic` targets `VERSION_21`.
- NDK `29.0.14206865` (`NativeEmulators.NDK_VERSION`); SDK CMake (`resolveSdkCmake`).
- `ubuntu-latest` ships Android SDK/NDK/CMake; pin the NDK version via `sdkmanager`
  if the preinstalled one differs.
