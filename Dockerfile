# syntax=docker/dockerfile:1.9
#
# Chipbox web app image — server + browser bundle + all nine WASM emulators.
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
# Toolchain pins live in docker/{node,yarn,emsdk}-version (shared with the CI web job). Every base
# image is pinned by a manifest-list digest (`image:tag@sha256:...`), so one pin resolves both
# linux/amd64 and linux/arm64. Reproducibility now means digest-pinned bases + the pinned toolchain
# + caches, not bit-for-bit image output. The recorded index/platform digests and the bump
# procedure live in docs/build.md ("Pinned base images"); scripts/ci/verify-docker-release.sh
# rejects a tag-only FROM.
#
# Multi-arch: the expensive, architecture-independent half (Kotlin/JS + all nine Emscripten builds)
# plus the server distribution runs once on $BUILDPLATFORM, so Gradle and emsdk never run under
# QEMU. Only the runtime stage is per-arch (JRE + distribution copy). The server ships no native
# library — scanning is pure Kotlin — so there is no per-arch native build.

# ---------------------------------------------------------------------------------------------
# web: Kotlin/JS bundle + Emscripten emulators + server distribution.
# Runs on the build platform (BUILDPLATFORM): Kotlin/JS and emsdk are architecture-independent, so
# a multi-arch build must not run this expensive stage under QEMU.
# ---------------------------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-jammy@sha256:742daacb9316be4b6fc60258f6e4ac2a11882f6c597373c92290a57bfad0d2e0 AS web

ARG NODE_VERSION=22.14.0
ARG YARN_VERSION=1.22.22
ARG EMSDK_VERSION=4.0.7

# Build identity for Settings > About. `.dockerignore` excludes `.git`, so the Gradle task can't
# `git describe` inside the image: CI passes the checkout's identity as build args (empty locally,
# where the task falls back to a neutral `0.0.0-web`).
ARG CHIPBOX_BUILD_VERSION=""
ARG CHIPBOX_BUILD_VERSION_CODE=""
ARG CHIPBOX_BUILD_COMMIT=""
ARG CHIPBOX_BUILD_BRANCH=""
ARG CHIPBOX_BUILD_TIME_MS=""

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
# (the server distribution ships no host native library).
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
# runtime: JRE 21 (Ubuntu/glibc). Explicit $TARGETPLATFORM so the JRE matches every single- and
# multi-arch build.
# ---------------------------------------------------------------------------------------------
FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre-jammy@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0 AS runtime

LABEL org.opencontainers.image.title="chipbox-server" \
      org.opencontainers.image.description="Chipbox self-hosted web client. NO AUTHENTICATION: loopback or trusted LAN only." \
      net.sigmabeta.chipbox.auth="none" \
      net.sigmabeta.chipbox.intended-use="loopback-or-trusted-lan-only"

# curl is the healthcheck; unzip exists only for the build-time contents assertion below and is
# purged in the same layer. /music is intentionally not writable (mount it read-only); /data is the
# persistent DB + RSN extraction work dir and must be writable by uid 10001.
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 chipbox \
    && useradd --uid 10001 --gid chipbox --home-dir /data --no-create-home \
        --shell /usr/sbin/nologin chipbox \
    && mkdir -p /opt/chipbox /data /music \
    && chown chipbox:chipbox /data

COPY --from=web /src/apps/server/build/install/chipbox-server /opt/chipbox
# The canonical nine-emulator list (also consumed by the bundle assertion and the smoke script).
COPY --from=web /src/apps/js/wasm-emulators.txt /tmp/wasm-emulators.txt

# Build-time proof that this architecture's image is deployable: the server jar actually carries
# index.html + every WASM loader/WASM pair (the web stage asserted the bundle on disk; this asserts
# what installDist packaged).
RUN set -eux; \
    jar=""; \
    for candidate in /opt/chipbox/lib/*.jar; do \
        if unzip -l "$candidate" 2>/dev/null | grep -q 'static/index.html'; then jar="$candidate"; break; fi; \
    done; \
    test -n "$jar"; \
    unzip -l "$jar" > /tmp/jar-contents.txt; \
    grep -q 'static/index.html' /tmp/jar-contents.txt; \
    grep -q 'static/chipbox.js' /tmp/jar-contents.txt; \
    count=0; \
    while IFS='|' read -r dir output display; do \
        dir="$(echo "$dir" | xargs)"; \
        output="$(echo "$output" | xargs)"; \
        [ -z "$dir" ] && continue; \
        case "$dir" in \#*) continue ;; esac; \
        grep -q "static/wasm/$output.js" /tmp/jar-contents.txt; \
        grep -q "static/wasm/$output.wasm" /tmp/jar-contents.txt; \
        count=$((count + 1)); \
    done < /tmp/wasm-emulators.txt; \
    test "$count" -eq 9; \
    rm -f /tmp/wasm-emulators.txt /tmp/jar-contents.txt; \
    apt-get purge -y unzip; \
    apt-get autoremove -y; \
    rm -rf /var/lib/apt/lists/*

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
