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

### Browser playback requires a secure context

The browser speaker is an `AudioWorklet`, and browsers expose `AudioWorklet` only
on **secure origins**. `http://localhost` and `http://127.0.0.1` qualify; a plain
`http://` URL to a LAN host does **not**. In that case the server still scans and
render-ahead caches tracks, and the UI still browses the library, but
`AudioContext.audioWorklet` is `undefined`, so playback can never start. The web
client detects this at startup and shows an **"Audio playback needs a secure
connection"** notification on Home.

For LAN access, terminate TLS in front of the container (Caddy/nginx with a
certificate for the host, either trusted or self-signed accepted once), or use an
SSH tunnel and browse through `localhost`:

```sh
ssh -L 8080:127.0.0.1:8080 your-nas
# then open http://localhost:8080
```

Do not expose the plain-HTTP port beyond the trusted LAN.

### Both architectures (no registry)

`--load` accepts one platform at a time, so a two-arch build is written as an OCI
archive. On an amd64 host, register arm64 binfmt once (Docker Desktop already
does this); the arm64 stage then runs the small native build under QEMU, while
the expensive Gradle/emsdk stage still runs once on the build host:

```sh
docker run --privileged --rm tonistiigi/binfmt --install arm64
docker buildx create --name chipbox --driver docker-container --use

mkdir -p build
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
**not published** to any registry, and the multi-arch OCI archive is not uploaded
as a workflow artifact either; there is **no public distribution** in v1. The
release job verifies the archive's index on the runner, and `publish` needs the
image job so an image failure blocks the release. Run
`scripts/ci/verify-docker-release.sh` to statically check the platform stages,
both-arch release wiring, no-push/no-artifact rule, native-lib architecture
assertion, deterministic build-identity args, pinned toolchain, and
digest-pinned base images.

### Pinned base images

Reproducibility means **digest-pinned base images + the pinned toolchain +
caches**, not bit-for-bit image output (apt, Node, emsdk, and Gradle still fetch
from the network). Every `FROM` in the `Dockerfile` keeps its human-readable tag
and adds the **manifest-list (OCI image index)** digest:

```dockerfile
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-jammy@sha256:742daacb… AS web
```

One index digest covers both `linux/amd64` and `linux/arm64`; the tag stays so
the pin is reviewable and so a human can tell which line to bump. Both JDK
stages (`web`, `native`) share one pin — `verify-docker-release.sh` fails a
half-applied bump that leaves the same tag on two different digests.

Recorded **2026-10-18** (index digest = `Dockerfile` pin; per-platform manifests
come from the index):

- `eclipse-temurin:21-jdk-jammy`
  - index `sha256:742daacb9316be4b6fc60258f6e4ac2a11882f6c597373c92290a57bfad0d2e0`
  - `linux/amd64` manifest `sha256:074d6f95f1e69965dbd23750a37ec3b234025d0864e9821e0b1fba0763a3e853`
  - `linux/arm64` manifest `sha256:e33a8f4ed06638221c62b6211ee90b86dca83d71a3a6d6488bf62895fbc81f10`
- `eclipse-temurin:21-jre-jammy`
  - index `sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0`
  - `linux/amd64` manifest `sha256:8ecbec1f32912979c15b86aaf98f7082a77d318a7f92af3d9fe2c0fd2723df10`
  - `linux/arm64` manifest `sha256:900b7ac325b25c3a7b67351d6595403eba379e7d35babed3cebbb0a9fdc54112`

The per-platform digests are recorded for audit/comparison, not used in the
`Dockerfile`: the index digest is what buildx resolves per target platform.

#### Bumping a base image

Manual, in one commit (there is no automated digest bot):

1. Resolve the tag's **index** digest and per-platform manifest digests from the
   registry. No Docker daemon is required (Docker Hub anonymous token flow):

   ```sh
   tag=21-jdk-jammy   # or 21-jre-jammy
   token=$(curl -fsSL \
     "https://auth.docker.io/token?service=registry.docker.io&scope=repository:library/eclipse-temurin:pull" \
     | jq -r .token)
   dir=$(mktemp -d)
   curl -fsSL -D "$dir/headers" -o "$dir/index.json" \
     -H "Authorization: Bearer $token" \
     -H "Accept: application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json" \
     "https://registry-1.docker.io/v2/library/eclipse-temurin/manifests/$tag"
   grep -i '^docker-content-digest:' "$dir/headers"   # index digest -> the Dockerfile pin
   jq -r '.manifests[]
     | select(.platform.os == "linux"
              and (.platform.architecture == "amd64" or .platform.architecture == "arm64"))
     | "linux/\(.platform.architecture) \(.digest)"' "$dir/index.json"
   rm -rf "$dir"
   ```

   With a Docker CLI, `docker manifest inspect eclipse-temurin:$tag` prints the
   same index (`--verbose` includes the platform manifests).

2. Update the `FROM` line(s) in `Dockerfile` **and** the table above in the same
   commit. Both JDK stages must move together. Keep the tag.
3. Run `scripts/ci/verify-docker-release.sh` (it fails a tag-only or malformed
   `FROM`, a dropped tag, and a half-bumped image).
4. On a Docker host, rebuild and smoke the release path: the amd64 image with
   `docker buildx build --platform linux/amd64 --load` plus
   `scripts/ci/web-image-smoke.sh`, then the multi-arch OCI build +
   `verify-oci-index.py` above. CI covers step 4 on the PR/release runners; this
   is the manual local gate.

### Build identity

`.dockerignore` excludes `.git`, so the image cannot run `git describe`. Pass the
checkout's identity as build args (CI does this automatically); without them the
image reports the neutral `0.0.0-web` dev version:

```sh
docker build -t chipbox-server:dev \
  --build-arg CHIPBOX_BUILD_VERSION="$(git describe --tags --always --dirty)" \
  --build-arg CHIPBOX_BUILD_VERSION_CODE="$(git rev-list --count HEAD)" \
  --build-arg CHIPBOX_BUILD_COMMIT="$(git rev-parse --short HEAD)" \
  --build-arg CHIPBOX_BUILD_BRANCH="$(git rev-parse --abbrev-ref HEAD)" \
  --build-arg CHIPBOX_BUILD_TIME_MS="$(( $(git log -1 --format=%ct) * 1000 ))" .
```

The same values appear in Settings → About (and the browser console at startup).
The env vars take precedence over git, so a source tarball/Docker build reports
the real version with no `.git`; each also has a `chipbox.build.*` Gradle-property
counterpart for local overrides.
