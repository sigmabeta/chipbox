# Release process

How Chipbox releases are cut and published. The migration that built this pipeline
is history (`ci-actions-migration.md`); **this** doc is the standing reference for
producing a release.

## TL;DR — cutting a release

Releases are **tag-triggered**. Push an annotated version tag from `beta`:

```sh
git tag -a 3.0.0-beta06 -m "Release 3.0.0-beta06"
git push origin 3.0.0-beta06
```

That's it. `.github/workflows/release.yml` fires on tags matching
`[0-9]+.[0-9]+*` (e.g. `3.0.0`, `3.0.0-beta06`, `3.1.0-rc1`), builds every
artifact, publishes a GitHub Release, and refreshes the download page. Nothing to
run by hand.

- A tag containing `alpha` / `beta` / `rc` is published as a **prerelease**.
- `workflow_dispatch` (Actions → Release → Run workflow) runs the **build** jobs
  only — the `publish` job is gated to tag refs, so a manual run never creates a
  Release. Useful for smoke-testing the build without cutting a version.

## What the pipeline does

`release.yml` has five jobs:

| Job | Runner | Produces |
|---|---|---|
| `desktop-linux` | ubuntu | `.deb` + `.rpm` (jpackage) |
| `windows-natives` | ubuntu | the emulator `.dll` (MinGW cross-compile) |
| `desktop-windows` | windows | `.msi` (jpackage over the prebuilt `.dll`) |
| `android-apk` | ubuntu | split signed APKs (universal + per-ABI) |
| `publish` | ubuntu | the GitHub Release + refreshed download page |

Desktop packaging is **jpackage** via `compose.desktop.application` in `apps/jvm`.
The emulator natives are bundled into the app image and `java.library.path` points
at them (`$APPDIR/resources`) — see the header comments in `apps/jvm/build.gradle.kts`.

### Why Windows is two jobs

jpackage can't cross-compile, but it also doesn't *compile* native code — it only
*assembles*. So we build the Windows `.dll` with the proven MinGW cross-compile on
a Linux job (`windows-natives`), hand them to a `windows-latest` job as an
artifact, and run jpackage there (`-Pchipbox.jvm.prebuiltNativeDir` skips the
native build and stages the prebuilt `.dll`). No native Windows toolchain needed.

The `.msi` step shells out to **WiX v3** (preinstalled on `windows-latest`; the job
`choco install`s it unpinned as a safety net). The Windows job also passes
`-Pchipbox.jvm.nativeTarget=windows-x64 -Pchipbox.jvm.nativeJdk=$JAVA_HOME` so the
`chipbox.native.host` plugin's eager apply resolves a win32-JNI-header JDK (the
runner's own).

## Versioning

Two version strings, on purpose:

- **Embedded OS-package version** (`.deb`/`.rpm` `Version`, `.msi` `ProductVersion`)
  = the tag's **numeric prefix** only, e.g. `3.0.0`. jpackage / rpm / msi version
  fields reject a prerelease suffix, so `-beta06` **cannot** go here. Set via
  `-Pchipbox.jvm.packageVersion`, sanitized in the workflow with
  `grep -oE '^[0-9]+\.[0-9]+\.[0-9]+'`.
- **Filename** = the **full tag**, e.g. `chipbox-3.0.0-beta06-x64.msi`. jpackage
  names files by the numeric version, so a "Label installers…" step renames them.

The **APKs** are versioned independently by the app-versioning plugin
(`scripts/rename-release-apk.sh` → `chipbox-<tag>.<commits>-<abi>.apk`, e.g.
`chipbox-3.0.0-beta06.0-universal.apk` — the `.0` is commits-since-tag).

## Published assets

For a tag `X` the Release carries (raw files, no zip wrapper):

- `chipbox-X-amd64.deb`, `chipbox-X-x86_64.rpm`, `chipbox-X-x64.msi`
- `chipbox-X.<commits>-universal.apk`, `-arm64-v8a.apk`, `-x86_64.apk`

macOS `.dmg` is **not produced yet** — it needs a darwin native build
(`NativeEmulators.NativeHostTarget` has only `LINUX` + `WINDOWS_X64`). When that
lands, add a `desktop-macos` job; the download page picks up a `.dmg` automatically.

The intermediate CI **artifacts** (Actions → run → Artifacts) are always zipped by
`upload-artifact` (`compression-level: 0`, since installers are already compressed);
the **Release** assets are the raw files.

## Download page automation

`publish` keeps the landing site's [download page](../docs/download.md) in sync:

1. `scripts/gen-download-page.sh <tag> <assets-dir>` regenerates `docs/download.md`
   from the **actual** published asset filenames (so the APK `.commits` suffix is
   picked up, and a platform with no asset — e.g. macOS — is simply omitted).
2. The refreshed page is committed to `beta`.
3. `pages.yml` is dispatched to redeploy.

A `GITHUB_TOKEN` commit can't trigger the push-based Pages build (recursion
prevention), which is why step 3 uses `workflow_dispatch` (exempt from that rule)
and the job needs `actions: write`. `docs/download.md` is **generated** — don't
hand-edit it; change the script.

## Secrets

Set as GitHub Actions repo secrets:

| Secret | Used by | Notes |
|---|---|---|
| `CHIPBOX_KEY_ALIAS` / `CHIPBOX_KEYSTORE_PASSWORD` / `CHIPBOX_KEY_PASSWORD` | `android-apk` | release APK signing; absent → debug-signed |
| `GRADLE_CACHE_USER` / `GRADLE_CACHE_PASSWORD` | all Gradle jobs | remote build-cache **push** (anonymous read always works). The node's write user is `ci`; leave `GRADLE_CACHE_USER` unset/blank to use that default (`settings.gradle.kts` treats blank as `ci`). |

Releases publish via the built-in `GITHUB_TOKEN` — **no PAT**.

## Verifying a release

CI proves the artifacts *build*, not that they *run*. After a release, sanity-check
on real hardware — especially the desktop installers, whose native `System.loadLibrary`
and `java.library.path` wiring CI can't exercise:

- Install the `.deb`/`.rpm`/`.msi` and **play a track through** (past the caching
  point) — confirms the bundled natives load and the cache writer completes.
- Check the launcher icon + that the app runs in **release** mode (title "Chipbox",
  not "Chipbox Debug" — the packaged app defaults `isDebug=false`; only the dev
  `./gradlew :apps:jvm:run` opts into debug via `-Dchipbox.debug=true`).

## Gotchas

- **jpackage needs a full JDK** (not a stripped JBR). CI's Temurin 21 has it; a dev
  machine building installers locally needs `-Pchipbox.jvm.jpackageJdk=/path/to/jdk`.
- **`publish` needs all three build jobs green** — a Windows failure skips publish,
  so a broken build never produces a partial Release.
- **Local dry run**: `./gradlew :apps:jvm:packageDeb :apps:jvm:packageRpm -Pchipbox.jvm.packageVersion=X.Y.Z -Pchipbox.jvm.jpackageJdk=…`
  builds the Linux installers without touching CI (needs `fakeroot`/`rpmbuild`).
