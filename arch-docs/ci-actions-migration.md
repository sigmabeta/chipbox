# CI migration: CircleCI → GitHub Actions

Status: **in progress** (started on branch `ci-github-actions`).

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
  android-lint, desktop-linux, desktop-windows, android-apk.
- `release.yml` — tag `^\d+\.\d+.*`: OS matrix (ubuntu `.deb`/`.rpm`, windows
  `.msi`, macos `.dmg`) + distZips + split APKs → publish GitHub Release.
- `pages.yml` — already exists, unchanged.

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
   every check matches green. CircleCI stays.
2. **Release** — `release.yml` (matrix installers + publish); test on a tag.
3. **macOS target** — build-logic + `.dmg`.
4. **Cut over** — delete `.circleci/config.yml` + CircleCI-only scripts; move secrets.
5. **(future)** iOS.

## Toolchain facts

- JDK **21** (Temurin) — `build-logic` targets `VERSION_21`.
- NDK `29.0.14206865` (`NativeEmulators.NDK_VERSION`); SDK CMake (`resolveSdkCmake`).
- `ubuntu-latest` ships Android SDK/NDK/CMake; pin the NDK version via `sdkmanager`
  if the preinstalled one differs.
