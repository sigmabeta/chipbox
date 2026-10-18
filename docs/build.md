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

## Web app (Docker)

The self-hosted browser app ships as a multi-stage image: the Kotlin/JS bundle and
all nine Emscripten emulators are built on `$BUILDPLATFORM`, the native
`libvgmstream.so` is built for `$TARGETPLATFORM`, and a non-root glibc JRE 21
runtime serves both. The `sage` submodule is a build input:

```sh
git submodule update --init --recursive
```

Build and run the host architecture:

```sh
docker build -t chipbox-server:dev .
docker run --rm -p 127.0.0.1:8080:8080 \
  -v /path/to/music:/music:ro -v chipbox-data:/data chipbox-server:dev
```

`/music` is read-only; `/data` holds the SQLite DB + RSN extraction work dir and
must be writable by uid `10001` (a bind mount needs an explicit `chown`).
**There is no authentication**: loopback or a trusted LAN only.

### Both architectures (no registry)

`--load` accepts one platform at a time, so a two-arch build is written as an OCI
archive. On an amd64 host, register arm64 binfmt once (Docker Desktop already
does this); the arm64 stage then runs the small native build under QEMU, while
the expensive Gradle/emsdk stage still runs once on the build host:

```sh
docker run --privileged --rm tonistiigi/binfmt --install arm64
docker buildx create --name chipbox --driver docker-container --use

docker buildx build --platform linux/amd64,linux/arm64 \
  --output type=oci,dest=build/chipbox-server-multiarch.tar \
  --cache-from type=local,src=.docker-cache \
  --cache-to type=local,dest=.docker-cache,mode=max .

python3 scripts/ci/verify-oci-index.py \
  build/chipbox-server-multiarch.tar linux/amd64 linux/arm64
```

For a runnable local image and the health/UI/WASM/native-lib checks, build one
platform and smoke it:

```sh
docker buildx build --platform linux/amd64 --load -t chipbox-server:dev .
scripts/ci/web-image-smoke.sh chipbox-server:dev --platform linux/amd64

# Or inspect the arm64 filesystem without QEMU (ELF arch + all nine WASM pairs):
docker buildx build --platform linux/arm64 --load -t chipbox-server:arm64 .
scripts/ci/web-image-smoke.sh chipbox-server:arm64 --platform linux/arm64 --contents-only
```

The image is built for amd64 on PRs and both architectures on release, but it is
**not published** to any registry and there is **no public distribution** in v1.
The release job uploads the OCI archive as a workflow artifact only. Run
`scripts/ci/verify-docker-release.sh` to statically check the platform stages,
both-arch release wiring, no-push rule, native-lib architecture assertion, and
pinned toolchain.
