# Chipbox — A Chiptune Jukebox for Android

Chipbox is a music player for Android that doesn't play MP3s. Instead it plays
the raw program/data dumps of old video game console sound chips and emulates
them in real time, reproducing the original soundtracks near-exactly from files
as small as a few dozen KiB.

It targets modern Android (minSdk 26 / Android 8.0+, targetSdk 36) and is built
entirely with Jetpack Compose, Hilt, and Kotlin coroutines.

## Supported Music Formats

Playback is provided by several emulator cores wired in via the
`cbox:*:player:emulators:*` modules:

- **GME** (game-music-emu) — SPC (SNES), NSF/NSFE (NES), GBS (Game Boy), AY,
  HES, KSS and more
- **VGM/VGZ** — Genesis / Mega Drive, 32X, Arcade and numerous other systems
- **PSF / miniPSF** (slopsf) — Sony PlayStation
- **GSF** (mGBA) — Game Boy Advance
- **SSF** — Sega Saturn
- **2SF** — Nintendo DS

## Architecture

Chipbox is modularized on top of the **SAGE** scaffold (included as the `sage`
git submodule). Modules follow an `api` / `di` / `real` / `fake` split:

- `app` — application entry point, wires the `:di` modules together
- `cbox/common/**` — platform-agnostic domain logic (player director, buffer,
  repository, scanner, settings, entities)
- `cbox/android/**` — Android-specific implementations (database, file content
  source, emulator JNI bridges, speaker output, UI chrome)
- `features/**` — Compose feature screens (library, browse, search, now-playing,
  game/artist detail, settings), each with `api` / `real` / `screenshot` modules
- `sage/` — build logic and shared infrastructure (submodule)

Native emulator cores are integrated through JNI; everything else is Kotlin.

## Building

```sh
git clone --recurse-submodules git@github.com:sigmabeta/chipbox.git
cd chipbox
./gradlew assembleDebug
```

If you already cloned without submodules, initialize the `sage` submodule first:

```sh
git submodule update --init --recursive
```

Install a debug build to a connected device:

```sh
./gradlew installDebug
```

Release builds are signed with `chipbox.jks` when the `CHIPBOX_KEY_ALIAS`,
`CHIPBOX_KEYSTORE_PASSWORD`, and `CHIPBOX_KEY_PASSWORD` environment variables are
present (set in CircleCI); local builds fall back to debug signing.

## Tooling

- **Gradle** with the Kotlin DSL, configuration cache, and version catalogs
- **Hilt** + **KSP** for dependency injection
- **Jetpack Compose** (Material 3) for the UI
- **ktlint** / **detekt** for static analysis (`./ktlint-check.sh`,
  `./ktlint-fix.sh`)
- **CircleCI** for CI

## Roadmap

- Add support for more emulator cores (USF / Nintendo 64 is scaffolded)
- Independent tempo & pitch playback controls (see
  `docs/psf-playback-speed-pitch-design.md`)
- Bespoke UI for Android TV
- Android Auto control support
