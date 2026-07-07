# Build from source

A modern, fully multiplatform Kotlin stack.

- **Kotlin Multiplatform** — shared code across Android and JVM
- **Compose Multiplatform** — one UI for both targets
- **Metro** — compile-time dependency injection
- **Voyager** — navigation
- **Kotlin Coroutines** — playback pipeline and async work
- **JNI emulator cores** — native chip emulation

## Android

Clone with the `sage` submodule and assemble a debug APK:

```sh
git clone --recurse-submodules git@github.com:sigmabeta/chipbox.git
cd chipbox
./gradlew :apps:android:assembleDebug
```

Already cloned without submodules? Initialize it first:

```sh
git submodule update --init --recursive
```

Install a debug build to a connected device:

```sh
./gradlew :apps:android:installDebug
```

## Desktop (JVM)

Native emulator cores are host-built via CMake. On Linux this runs natively:

```sh
./gradlew :apps:jvm:run
```

Package a distributable archive:

```sh
./gradlew :apps:jvm:installDist   # or distZip / distTar
```

### Windows (cross-compiled)

A Windows distribution is cross-compiled from Linux with the MinGW-w64 toolchain
(all nine cores, as fully static `.dll`s). Install the cross toolchain
(`mingw-w64` + a static MinGW zlib) and point at a Windows JDK for the win32 JNI
headers:

```sh
./gradlew :apps:jvm:distZip -Pchipbox.jvm.nativeTarget=windows-x64 \
  -Pchipbox.jvm.nativeJdk=/path/to/windows-jdk
```

### macOS (Apple Silicon)

On a macOS host the cores build natively to `.dylib`, so `./gradlew :apps:jvm:run`
works as on Linux. Package an installer image with jpackage:

```sh
./gradlew :apps:jvm:packageDmg
```

The `.dmg` is Apple Silicon (arm64) only and currently ships **unsigned** —
Gatekeeper will warn on first launch (right-click → Open, or
`xattr -dr com.apple.quarantine`). Signing and notarization are still a TODO.
