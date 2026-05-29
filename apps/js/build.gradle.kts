plugins {
    // Kotlin Multiplatform plugin is on the classpath via includeBuild("sage-build-logic"), so
    // apply it without a version (a versioned alias fails with "already on the classpath with an
    // unknown version"). Compose plugins do come from this project's catalog and work via alias.
    kotlin("multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
    // RemoteRepository serializes responses from the chipbox-server JSON API via kotlinx-serialization.
    // The models themselves are already @Serializable (cbox/common/models/api); this plugin just
    // pulls the codegen into apps/js so the Ktor JS client can call `body<List<Game>>()`.
    alias(libs.plugins.kotlin.serialization)
}

// Sister to apps/jvm + apps/android, but browser-only. sage.kmp / sage.compose.kmp aren't applied
// because they force `jvm()` + an Android library target and `jvmSharedMain` `dependsOn` edges
// that don't belong in a single-target executable. The Kotlin Multiplatform + Compose plugins
// above are what sage.compose.kmp adds on top of sage.kmp anyway — minus the Android pieces.

// ---------------------------------------------------------------------------------------------
// WASM emulator builds (Emscripten). Each chiptune emulator's `cbox/native/<name>` directory
// has a CMakeLists with an EMSCRIPTEN branch that emits a `.js` loader + `.wasm` blob via
// emcmake/emmake. Output lands in `build/wasm/<name>/`; the `copyEmulatorWasm` task fans it into
// `src/jsMain/resources/wasm/` so the webpack bundle ships them at `/wasm/<name>.wasm` runtime.
//
// emsdk discovery: explicit `-Pchipbox.js.emsdk=/path/to/emsdk` first, then `$EMSDK` env var.
// Tasks fail at execution (not configuration) if neither is present, so running unrelated tasks
// (`compileKotlinJs` etc.) on a dev box without emsdk still works.
val emsdkDir: Provider<String> = providers.gradleProperty("chipbox.js.emsdk")
    .orElse(providers.environmentVariable("EMSDK"))

val nativeGmeDirPath: String =
    rootProject.layout.projectDirectory.dir("cbox/native/gme").asFile.absolutePath
val wasmGmeBuildDirPath: String = layout.buildDirectory.dir("wasm/gme").get().asFile.absolutePath
val wasmResourcesDirPath: String =
    layout.projectDirectory.dir("src/jsMain/resources/wasm").asFile.absolutePath

val configureGmeWasm = tasks.register<Exec>("configureGmeWasm") {
    description = "Configures the Emscripten WASM build for libgme. Needs \$EMSDK or " +
        "-Pchipbox.js.emsdk."
    group = "wasm"
    inputs.file(File(nativeGmeDirPath, "CMakeLists.txt"))
    outputs.file(File(wasmGmeBuildDirPath, "CMakeCache.txt"))

    // Capture into locals so the doFirst closure only references config-cache-safe values
    // (String paths and a Provider<String>) — not script-level fields or methods.
    val capturedEmsdk = emsdkDir
    val buildDir = wasmGmeBuildDirPath
    val srcDir = nativeGmeDirPath
    doFirst {
        val emsdk = capturedEmsdk.orNull
            ?: error("Emscripten SDK not found. Install emsdk and set \$EMSDK, or pass " +
                "-Pchipbox.js.emsdk=/path/to/emsdk.")
        require(File(emsdk, "emsdk_env.sh").isFile) {
            "EMSDK=$emsdk does not look like an emsdk checkout (no emsdk_env.sh)."
        }
        File(buildDir).mkdirs()
        val envScript = File(emsdk, "emsdk_env.sh").absolutePath
        commandLine(
            "bash", "-c",
            "source '$envScript' >/dev/null 2>&1 && " +
                "emcmake cmake -B '$buildDir' -S '$srcDir' -DCMAKE_BUILD_TYPE=Release",
        )
    }
}

val buildGmeWasm = tasks.register<Exec>("buildGmeWasm") {
    description = "Builds chipbox_gme.wasm + chipbox_gme.js via Emscripten."
    group = "wasm"
    dependsOn(configureGmeWasm)
    inputs.dir(File(nativeGmeDirPath, "gme"))
    inputs.file(File(nativeGmeDirPath, "Gme_Web.cpp"))
    outputs.file(File(wasmGmeBuildDirPath, "chipbox_gme.wasm"))
    outputs.file(File(wasmGmeBuildDirPath, "chipbox_gme.js"))

    val capturedEmsdk = emsdkDir
    val buildDir = wasmGmeBuildDirPath
    doFirst {
        val emsdk = capturedEmsdk.orNull
            ?: error("Emscripten SDK not found. Install emsdk and set \$EMSDK, or pass " +
                "-Pchipbox.js.emsdk=/path/to/emsdk.")
        val envScript = File(emsdk, "emsdk_env.sh").absolutePath
        commandLine(
            "bash", "-c",
            "source '$envScript' >/dev/null 2>&1 && emmake make -C '$buildDir' -j",
        )
    }
}

val copyEmulatorWasm = tasks.register<Copy>("copyEmulatorWasm") {
    description = "Copies built emulator WASM blobs into apps/js resources so webpack bundles " +
        "them with the JS app (served at /wasm/<name>.{js,wasm} runtime)."
    group = "wasm"
    dependsOn(buildGmeWasm)
    from(wasmGmeBuildDirPath) {
        include("chipbox_gme.js", "chipbox_gme.wasm")
    }
    into(wasmResourcesDirPath)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "chipbox.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        named("jsMain") {
            dependencies {
                implementation(projects.cbox.common.appui.api)
                implementation(projects.cbox.common.strings.real)
                implementation(projects.cbox.common.strings.api)

                // Library data comes from the chipbox-server JSON API via RemoteRepository (see
                // jsMain/.../repository/RemoteRepository.kt). The other fakes stay because the
                // browser still has no audio chain — no emulator C++ JNI, no filesystem, no
                // playback. Settings/Debug/DebugInfo are also fakes (no server-side persistence
                // of user settings in v1).
                implementation(projects.cbox.common.repository.api)
                implementation(projects.cbox.common.contentsource.api)
                implementation(projects.cbox.common.contentsource.fake)
                implementation(projects.cbox.common.player.speaker.api)
                implementation(projects.cbox.common.player.speaker.fake)
                implementation(projects.cbox.common.player.generator.api)
                implementation(projects.cbox.common.player.generator.fake)
                implementation(projects.cbox.common.player.director.api)
                implementation(projects.cbox.common.player.director.fake)
                implementation(projects.cbox.common.player.director.real)
                implementation(projects.cbox.common.player.emulators.api)
                implementation(projects.cbox.common.player.buffer.api)
                implementation(projects.cbox.common.player.buffer.real)
                implementation(projects.cbox.common.player.generator.real)
                implementation(projects.cbox.common.player.cache.api)
                implementation(projects.cbox.common.player.cache.real)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.settings.api)
                implementation(projects.cbox.common.settings.fake)
                implementation(projects.cbox.common.debug.api)
                implementation(projects.cbox.common.debug.fake)
                implementation(projects.cbox.common.debugInfo.api)
                implementation(projects.cbox.common.debugInfo.fake)
                implementation(projects.cbox.common.scanner.api)
                implementation(projects.cbox.common.scanner.fake)
                implementation(projects.cbox.common.models.api)

                // VM supertypes — kept explicit so Kotlin/JS doesn't warn "Cannot access ...
                // supertype". The compiler still works without these (they're transitive), but
                // declaring direct deps for every type WebGraph constructs keeps incremental
                // builds clean.
                implementation(projects.cbox.common.ui.vm.api)
                implementation(projects.cbox.common.ui.list.api)
                implementation(projects.cbox.common.ui.freeform.api)
                implementation(projects.cbox.common.appcomm.api)
                implementation(libs.sage.common.freeform)
                implementation(libs.sage.common.list)

                // Every feature :real module that has a non-assisted VM the WebMetroViewModelFactory
                // can construct directly. Detail screens (game/artist/games-for-platform) and the
                // folder picker use @Assisted constructors — those would need ViewModelAssistedFactory
                // wiring; clicking into them on JS will currently throw "Unknown model class".
                implementation(projects.cbox.common.playerStatus.api)
                implementation(projects.features.home.real)
                implementation(projects.features.library.real)
                implementation(projects.features.search.real)
                implementation(projects.features.settings.real)
                implementation(projects.features.manageLibrary.real)
                implementation(projects.features.rescanStatus.real)
                implementation(projects.features.nowPlaying.real)
                implementation(projects.features.browseAllTracks.real)
                implementation(projects.features.browseByArtist.real)
                implementation(projects.features.browseByGame.real)
                implementation(projects.features.browseByPlatform.real)
                implementation(projects.features.playbackStatus.real)

                implementation(libs.sage.common.di)
                implementation(libs.sage.common.logging)
                implementation(libs.sage.common.appinfo)
                implementation(libs.sage.common.ui.perfCompose)
                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                // FolderPicker's OkioFolderLister injects okio.FileSystem; the browser doesn't have
                // a real filesystem, so a FakeFileSystem keeps the graph closed. The JS folder
                // picker Route is a stub anyway.
                implementation(libs.okio)
                implementation(libs.okio.fakefilesystem)
                // okio's JS bundle calls `require('os').tmpdir()` from `Path.SYSTEM_TEMPORARY_DIRECTORY`'s
                // static initializer (runs at FileSystem class-load, which happens during
                // FakeFileSystem construction). `os-browserify` is the canonical empty-ish stub
                // — provides tmpdir/homedir/platform/EOL with browser-safe defaults. Wired into
                // the webpack `resolve.fallback.os` in `webpack.config.d/okio-node-polyfills.js`.
                implementation(npm("os-browserify", "0.3.0"))

                // Ktor JS HTTP client + JSON content negotiation — RemoteRepository fans every
                // Repository call out to GET/POST/DELETE against chipbox-server.
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui)

                implementation(libs.androidx.lifecycle.viewmodel)
            }
        }
    }
}
