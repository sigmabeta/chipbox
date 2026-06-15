# Chipbox — A Chiptune Jukebox

Chipbox is a music player that doesn't play MP3s. Instead it plays the raw
program/data dumps of old video game console sound chips and emulates them in
real time, reproducing the original soundtracks near-exactly from files as small
as a few dozen KiB.

It targets modern Android (minSdk 26 / Android 8.0+, targetSdk 36) and also runs
as a desktop (JVM) application. The codebase is Kotlin Multiplatform, with a
shared Compose Multiplatform UI, Metro dependency injection, and Kotlin
coroutines.

## Supported Music Formats

Playback is provided by several emulator cores wired in via the
`cbox:*:player:emulators:*` modules:

- **GME** (game-music-emu) — SPC (SNES), NSF/NSFE (NES), GBS (Game Boy), AY,
  HES, KSS and more
- **VGM/VGZ** — Genesis / Mega Drive, 32X, Arcade and numerous other systems
- **PSF / miniPSF** (slopsf) — Sony PlayStation
- **GSF** (mGBA) — Game Boy Advance
- **USF / miniUSF** — Nintendo 64
- **SSF** — Sega Saturn
- **2SF** — Nintendo DS (DS Sound Format)
- **NCSF / miniNCSF** (SSEQ-Player) — Nintendo DS (Nitro Composer)
- **vgmstream** — streamed game audio (ADX, HCA, DSP, STRM, and many more
  single-file formats)

## Architecture

Chipbox is a Kotlin Multiplatform project built on top of the **SAGE** scaffold
(included as the `sage` git submodule). Most modules follow an
`api` / `di` / `real` / `fake` split, with an `all` aggregator:

- `apps/android` — the Android application; `apps/jvm` — the desktop (JVM)
  application. Both wire the `:di` modules together and host the shared Compose
  UI.
- `cbox/common/**` — platform-agnostic, multiplatform code shared by both apps
  (player director, buffer, repository, scanner, settings, entities, and the
  Compose UI shell)
- `cbox/android/**` — Android-specific implementations (database, file content
  source, emulator JNI bridges, speaker output)
- `features/**` — Compose feature screens (library, browse, search, now-playing,
  game/artist detail, settings), most with `api` / `real` / `screenshot` modules
- `sage/` — build logic and shared infrastructure (submodule)

Dependency injection is **Metro** (with Dagger-annotation interop). Navigation
uses **Voyager**. Native emulator cores are integrated through JNI; everything
else is Kotlin.

## Building

```sh
git clone --recurse-submodules git@github.com:sigmabeta/chipbox.git
cd chipbox
./gradlew :apps:android:assembleDebug
```

If you already cloned without submodules, initialize the `sage` submodule first:

```sh
git submodule update --init --recursive
```

Install a debug build to a connected device:

```sh
./gradlew :apps:android:installDebug
```

Run the desktop app (native emulator libs are host-built via CMake; Linux-only
for now):

```sh
./gradlew :apps:jvm:run
```

Release builds are signed with `chipbox.jks` when the `CHIPBOX_KEY_ALIAS`,
`CHIPBOX_KEYSTORE_PASSWORD`, and `CHIPBOX_KEY_PASSWORD` environment variables are
present (set in CircleCI); local builds fall back to debug signing.

## Testing

Beyond unit tests and Paparazzi screenshots, Chipbox has a **cross-platform UI
test framework** (`:cbox:common:uitest`). A test scripts the real Compose UI —
the actual `ChipboxAppUi` shell, real ViewModels, real navigation — over fake
data via a small DSL:

```kotlin
runChipboxUiTest {
    startAtScreen(GameDetail(firstGame().id))
    assertTitle("Metal Slug")
    clickWideItem(name = "JIM")
    assertNavigationEvent(ArtistDetail(3023))
    assertDirectorReceived(SessionRequest.Play)
}
```

The same specs run on two targets:

```sh
./gradlew :cbox:common:uitest:jvmTest                  # desktop JVM (headless, fast)
./gradlew :cbox:common:uitest:connectedAndroidDeviceTest   # on a connected device/emulator
```

When a spec fails, the harness dumps a **screenshot** and a **semantics-tree
dump** of the live scene (named `<TestClass>.<method>.png` /
`<TestClass>.<method>-semantics.txt`) before rethrowing:

- **desktop** — `cbox/common/uitest/build/uitest-failures/`
- **on-device** — pulled back to
  `cbox/common/uitest/build/outputs/connected_android_test_additional_output/androidDeviceTest/connected/<device>/`

See `docs/architecture/ui-test-dsl.md` for the design (verbs, the Metro test
graph, and the source-set topology).

## Tooling

- **Gradle** with the Kotlin DSL, configuration cache, and version catalogs
- **Metro** for dependency injection
- **Compose Multiplatform** (Material 3) for the shared UI
- **Voyager** for navigation
- **Paparazzi** for screenshot tests (`./gradlew verifyPaparazziDebug`)
- **ktlint** / **detekt** for static analysis (`./gradlew ktlintCheck detekt`;
  `./gradlew ktlintFormat` auto-fixes)
- **CircleCI** for CI (Android and JVM build pipelines)

## Roadmap

- Independent tempo & pitch playback controls (see
  `docs/psf-playback-speed-pitch-design.md`)
- Desktop builds for macOS and Windows (currently Linux-only)
- Bespoke UI for Android TV
- Android Auto control support
- Add support for more emulator cores
