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

Native emulator cores are host-built via CMake (Linux-only for now):

```sh
./gradlew :apps:jvm:run
```

Package a distributable archive:

```sh
./gradlew :apps:jvm:installDist   # or distZip / distTar
```
