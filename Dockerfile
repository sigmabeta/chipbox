# syntax=docker/dockerfile:1.9
#
# Chipbox web app image — server + browser bundle + all nine WASM emulators + native vgmstream.
#
#   docker build -t chipbox-server:dev .
#   docker run --rm -p 127.0.0.1:8080:8080 \
#     -v /path/to/music:/music:ro -v chipbox-data:/data chipbox-server:dev
#
# /music is read-only and scanned on startup. /data is the work dir + SQLite DB and must persist;
# a bind-mounted /data must be writable by uid 10001 (or run with --user "$(id -u):$(id -g)").
#
# NO AUTHENTICATION. This image is for loopback or a trusted-LAN deployment only. It must not be
# published to a registry or exposed to the internet until an auth story exists (ADR-0003).
#
# Build prerequisites: the sage submodule must be checked out — this is a Gradle build input:
#   git submodule update --init --recursive
#
# Toolchain pins live in docker/{node,yarn,emsdk}-version (shared with the CI web job). The build is
# reproducible in the "same pinned toolchain + caches" sense, not bit-for-bit.

# ---------------------------------------------------------------------------------------------
# web: Kotlin/JS bundle + Emscripten emulators + server distribution.
# Runs on the build platform (BUILDPLATFORM): Kotlin/JS and emsdk are architecture-independent, so
# a multi-arch build must not run this expensive stage under QEMU.
# ---------------------------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-jammy AS web

ARG NODE_VERSION=22.14.0
ARG YARN_VERSION=1.22.22
ARG EMSDK_VERSION=4.0.7

SHELL ["/bin/bash", "-c"]

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl git python3 build-essential cmake unzip xz-utils \
    && rm -rf /var/lib/apt/lists/*

# Node: pinned tarball, architecture detected from the build platform.
RUN set -eux; \
    arch="$(dpkg --print-architecture)"; \
    case "$arch" in \
        amd64) node_arch=x64 ;; \
        arm64) node_arch=arm64 ;; \
        *) echo "unsupported build platform architecture: $arch" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${node_arch}.tar.xz" \
        -o /tmp/node.tar.xz; \
    tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1; \
    rm /tmp/node.tar.xz; \
    npm install -g "yarn@${YARN_VERSION}"; \
    node --version; yarn --version

# emsdk: pinned tag, installed + activated once in the image.
RUN set -eux; \
    git clone --depth 1 --branch "${EMSDK_VERSION}" \
        https://github.com/emscripten-core/emsdk.git /opt/emsdk; \
    /opt/emsdk/emsdk install "${EMSDK_VERSION}"; \
    /opt/emsdk/emsdk activate "${EMSDK_VERSION}"; \
    test -x /opt/emsdk/upstream/emscripten/emcc
ENV EMSDK=/opt/emsdk

WORKDIR /src
COPY . .

RUN test -f sage/settings.gradle.kts \
    || { echo "sage submodule missing in build context; run: git submodule update --init --recursive" >&2; exit 1; }

# The Gradle build reads these pins too: the CI job builds the same emsdk, and the bundle assertion
# below is the same script CI runs. -Pchipbox.skipNative keeps the web stage free of an Android SDK
# (the native vgmstream library is built in the architecture-specific `native` stage).
# The Gradle WASM tasks source $EMSDK/emsdk_env.sh in their own subshells, so the image's PATH
# keeps the pinned Node 22 (emsdk's bundled Node must not shadow it).
RUN ./gradlew \
        :apps:js:verifyWebBundle \
        :apps:server:installDist \
        -Psage.js=true \
        -Pchipbox.skipNative \
        -Dorg.gradle.isolated-projects=false \
        -Dorg.gradle.configureondemand=false \
        --no-parallel --no-daemon --max-workers=4

RUN scripts/ci/verify-web-bundle.sh

# ---------------------------------------------------------------------------------------------
# native: libvgmstream for the *target* architecture (Linux .so). Runs under QEMU for a
# cross-arch build; it is one small CMake library, so that is tolerable. Configure-on-demand keeps
# the Android native modules (and their SDK/NDK requirement) out of this stage.
# ---------------------------------------------------------------------------------------------
FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jdk-jammy AS native

RUN apt-get update && apt-get install -y --no-install-recommends \
        cmake gcc g++ make \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .

RUN test -f sage/settings.gradle.kts \
    || { echo "sage submodule missing in build context; run: git submodule update --init --recursive" >&2; exit 1; }

RUN ./gradlew :apps:jvm:buildHostNativeVgmstream \
        -Dorg.gradle.isolated-projects=false \
        --configure-on-demand \
        -Pchipbox.cmake="$(command -v cmake)" \
        --no-daemon --max-workers=4

# ---------------------------------------------------------------------------------------------
# runtime: JRE 21 (Ubuntu/glibc — the vgmstream .so is glibc-linked, so no alpine).
# ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

LABEL org.opencontainers.image.title="chipbox-server" \
      org.opencontainers.image.description="Chipbox self-hosted web client. NO AUTHENTICATION: loopback or trusted LAN only." \
      net.sigmabeta.chipbox.auth="none" \
      net.sigmabeta.chipbox.intended-use="loopback-or-trusted-lan-only"

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 chipbox \
    && useradd --uid 10001 --gid chipbox --home-dir /data --no-create-home \
        --shell /usr/sbin/nologin chipbox \
    && mkdir -p /opt/chipbox /data /music \
    && chown chipbox:chipbox /data

COPY --from=web /src/apps/server/build/install/chipbox-server /opt/chipbox
COPY --from=native /src/apps/jvm/build/jvm-native/out/linux/vgmstream/host/libvgmstream.so \
     /opt/chipbox/lib/native/libvgmstream.so

ENV CHIPBOX_LIBRARY_DIR=/music \
    CHIPBOX_WORK_DIR=/data \
    CHIPBOX_DB_PATH=/data/library.sqlite \
    CHIPBOX_PORT=8080 \
    CHIPBOX_BIND_HOST=0.0.0.0

# Read-only music mount; the DB + RSN extraction work dir live here. See the run command above.
VOLUME ["/data"]

USER 10001:10001
EXPOSE 8080

# The server serves immediately and scans in the background; health returns 200 while scanning.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl -fsS "http://127.0.0.1:${CHIPBOX_PORT}/api/health" || exit 1

ENTRYPOINT ["/opt/chipbox/bin/chipbox-server"]
