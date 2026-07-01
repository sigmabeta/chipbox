# CI migration: CircleCI → GitHub Actions

Status: **done, merged to `beta`.** CircleCI is deleted; GitHub Actions is the
only CI — `ci.yml` (push/PR) and `release.yml` (tags). Linux `.deb`/`.rpm` +
Windows `.msi` + signed APKs ship; `3.0.0-beta05` was the first release cut through
it. The macOS `.dmg` target is wired (`desktop-macos`) — pending first-run
validation; see `release-process.md`.

> This doc is the **historical migration record**. For how releases work now and
> how to cut one, see **[`release-process.md`](release-process.md)**.

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
- `release.yml` — tag `^\d+\.\d+.*`: builds the desktop installers + APKs and
  publishes a GitHub Release via the built-in `GITHUB_TOKEN`.
  - **Linux desktop** → `.deb` + `.rpm` (JRE bundled), via jpackage
    (`compose.desktop.application` in `apps/jvm`). Validated locally (natives
    bundled at `$APPDIR/resources`, `java.library.path` points there).
  - **Windows desktop** → `.msi`. Two-stage: a ubuntu job MinGW-cross-compiles the
    emulator `.dll`, then a `windows-latest` job runs jpackage over them
    (`-Pchipbox.jvm.prebuiltNativeDir`). jpackage assembles but doesn't compile
    native code, so no native Windows toolchain is needed. **Unvalidated** — wired
    blind (no local Windows); the first tag is its live test.
  - **APKs** → split signed APKs (unchanged).
  - **macOS** → not produced (see below).
- `pages.yml` — already exists, unchanged.

> **Packaging migration (done for desktop):** `apps/jvm` moved off the Gradle
> `application` plugin to `compose.desktop.application` — the two can't coexist
> (both register a `run` task). Consequences: `:apps:jvm:distZip` no longer exists
> (replaced by the `.deb`/`.rpm` installers); `run` is now Compose's JavaExec
> (`--args="gui"` still works); the natives + splash are bundled via
> `appResourcesRootDir` and `java.library.path=$APPDIR/resources` instead of the
> old start-script injection. jpackage needs a full JDK — CI's Temurin 21 has it;
> a dev JBR needs `-Pchipbox.jvm.jpackageJdk=…`.

> **Windows .msi approach:** jpackage can't cross-compile, but it only *assembles*
> — it never compiles native code. So the existing MinGW cross-compile (Linux)
> still produces the `.dll`, and a `windows-latest` job runs jpackage over those
> prebuilt libs (`-Pchipbox.jvm.prebuiltNativeDir` skips the native build and
> stages them instead). No native Windows toolchain. Likely failure points to
> check on the first run: WiX availability (jpackage `.msi` needs WiX v3 — the job
> installs it via choco), the forward-slash `java.library.path=$APPDIR/resources`
> resolving on Windows, and whether the `.dll` actually load (the old cross-built
> distZip was never run on Windows either).

> **macOS `.dmg` (added, pending validation):** `NativeHostTarget.MACOS` now builds
> the cores natively to `.dylib` on `macos-latest`, and a `desktop-macos` job
> packages the `.dmg` (arm64, unsigned). Written blind (no macOS host) — the darwin
> clang compile of the cores and the runtime `.dylib` loading are unverified until a
> CI run + a real install. See `release-process.md`.

> **Native-host build reality (verified):** the desktop native build supports
> only `LINUX` (native to the build machine) and `WINDOWS_X64` (MinGW-w64
> cross-compile from Linux) — `NativeEmulators.NativeHostTarget`. There is **no
> native Windows-host path and no macOS/`darwin` target**, and `apps/jvm` has no
> `nativeDistributions` block (now added — Linux only). So "Windows: go native on
> `windows-latest`" and macOS `.dmg` remain **blocked on new build-logic**, not
> just new CI YAML.

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
   - **2b Native installers** — `apps/jvm` migrated to
     `compose.desktop.application`; jpackage bundles the JNI natives into the app
     image. **Linux `.deb`/`.rpm` done** (validated locally). **Windows `.msi`
     wired** (MinGW-cross `.dll` on ubuntu → jpackage on `windows-latest`;
     unvalidated — needs a tag). `.dmg` on `macos-latest` is **pending the darwin
     native build**.
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
