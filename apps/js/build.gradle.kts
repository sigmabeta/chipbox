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
//
// REQUIRES `-Psage.js=true`. The shared modules this app depends on only expose a Kotlin/JS
// variant when that property is set (see `sage.kmp.js`); the gate keeps non-web Android/JVM builds
// from configuring Kotlin/JS at all (it's incompatible with configure-on-demand). Any apps/js or
// apps/server bundle task must pass it.

// ---------------------------------------------------------------------------------------------
// WASM emulator builds (Emscripten). Each chiptune emulator's `cbox/native/<name>` directory
// has a CMakeLists with an EMSCRIPTEN branch that emits a `.js` loader + `.wasm` blob via
// emcmake/emmake. Output lands in `build/wasm/<name>/`; the `copyEmulatorWasm` task fans it into
// `src/jsMain/resources/wasm/` so the webpack bundle ships them at `/wasm/<name>.wasm` runtime.
//
// emsdk discovery: explicit `-Psage.js.emsdk=/path/to/emsdk` first, then `$EMSDK` env var.
// Tasks fail at execution (not configuration) if neither is present, so running unrelated tasks
// (`compileKotlinJs` etc.) on a dev box without emsdk still works. Every WASM-dependent task also
// depends on `checkEmsdk`, so a bundle build fails up front instead of quietly copying nothing.
//
// The validation is inlined into each `doFirst` (rather than calling a script-level helper) because
// a helper reference captures the Kotlin build script object, which the configuration cache refuses
// to serialize.
val emsdkDir: Provider<String> = providers.gradleProperty("sage.js.emsdk")
    .orElse(providers.environmentVariable("EMSDK"))
val emsdkMissingMessage =
    "Emscripten SDK not found. The web bundle needs the WASM emulators: install emsdk and set " +
        "\$EMSDK, or pass -Psage.js.emsdk=/path/to/emsdk. (Unrelated Kotlin/JS compile tasks still " +
        "work without it.)"

val wasmResourcesDirPath: String =
    layout.projectDirectory.dir("src/jsMain/resources/wasm").asFile.absolutePath
// isolated.rootProject (not rootProject.layout) keeps this root-relative lookup Isolated-Projects-safe.
val nativeRootDir: String = isolated.rootProject.projectDirectory.dir("cbox/native").asFile.absolutePath
val wasmBuildRootDir: String = layout.buildDirectory.dir("wasm").get().asFile.absolutePath

// Each emulator that ships a WASM build is one line in `wasm-emulators.txt` (shared with the
// bundle-verification script so the two cannot drift). The CMakeLists in `cbox/native/<dir>/` must
// have an `if(EMSCRIPTEN)` branch that produces a target named `chipbox_<output>` (Emscripten
// emits `.js` + `.wasm` for executable targets). The Kotlin side loads each via a
// `<Capitalized>Wasm.kt` module-loader file. New emulator → one entry there, one CMake branch, one
// `<Name>_Web.cpp` wrapper, one `Wasm<Name>Emulator.kt` subclass.
data class WasmEmulator(val dir: String, val output: String, val displayName: String)
val wasmEmulators: List<WasmEmulator> = providers
    .fileContents(layout.projectDirectory.file("wasm-emulators.txt"))
    .asText.get()
    .lineSequence()
    .map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .map { line ->
        val fields = line.split('|').map(String::trim)
        check(fields.size == 3) { "wasm-emulators.txt: expected dir|output|display, got '$line'" }
        WasmEmulator(fields[0], fields[1], fields[2])
    }
    .toList()
check(wasmEmulators.isNotEmpty()) { "wasm-emulators.txt listed no emulators." }

val checkEmsdk = tasks.register("checkEmsdk") {
    description = "Fails fast (at execution) when the Emscripten SDK needed for the WASM emulators " +
        "is missing. The web bundle cannot be assembled without it."
    group = "wasm"
    val capturedEmsdk = emsdkDir
    val missingMessage = emsdkMissingMessage
    doFirst {
        val emsdk = capturedEmsdk.orNull ?: error(missingMessage)
        require(File(emsdk, "emsdk_env.sh").isFile) {
            "EMSDK=$emsdk does not look like an emsdk checkout (no emsdk_env.sh)."
        }
        require(File(emsdk, "upstream/emscripten/emcc").isFile) {
            "emsdk at $emsdk has no activated compiler (upstream/emscripten/emcc); run " +
                "`./emsdk install <version> && ./emsdk activate <version>`."
        }
    }
}

val buildTaskNames = wasmEmulators.map { emu ->
    val cap = emu.dir.replaceFirstChar { it.uppercaseChar() }
    val srcDir = "$nativeRootDir/${emu.dir}"
    val buildDir = "$wasmBuildRootDir/${emu.dir}"

    val configureTask = tasks.register<Exec>("configure${cap}Wasm") {
        description = "Configures the Emscripten WASM build for ${emu.displayName}. Needs " +
            "\$EMSDK or -Psage.js.emsdk."
        group = "wasm"
        dependsOn(checkEmsdk)
        inputs.file(File(srcDir, "CMakeLists.txt"))
        // Shared psflib/psf-io sources + their CMakeLists are part of this emulator's build.
        inputs.file(File("$nativeRootDir/native-common", "CMakeLists.txt"))
        outputs.file(File(buildDir, "CMakeCache.txt"))

        val capturedEmsdk = emsdkDir
        val missingMessage = emsdkMissingMessage
        val buildDirLocal = buildDir
        val srcDirLocal = srcDir
        doFirst {
            val emsdk = capturedEmsdk.orNull ?: error(missingMessage)
            require(File(emsdk, "emsdk_env.sh").isFile) {
                "EMSDK=$emsdk does not look like an emsdk checkout (no emsdk_env.sh)."
            }
            require(File(emsdk, "upstream/emscripten/emcc").isFile) {
                "emsdk at $emsdk has no activated compiler (upstream/emscripten/emcc); run " +
                    "`./emsdk install <version> && ./emsdk activate <version>`."
            }
            File(buildDirLocal).mkdirs()
            val envScript = File(emsdk, "emsdk_env.sh").absolutePath
            commandLine(
                "bash",
                "-c",
                "source '$envScript' >/dev/null 2>&1 && " +
                    "emcmake cmake -B '$buildDirLocal' -S '$srcDirLocal' -DCMAKE_BUILD_TYPE=Release",
            )
        }
    }

    val buildTask = tasks.register<Exec>("build${cap}Wasm") {
        description = "Builds ${emu.output}.wasm + ${emu.output}.js via Emscripten."
        group = "wasm"
        dependsOn(configureTask)
        // Source-tree inputs that should trigger a rebuild. The entire native dir minus the
        // build dir would be most robust, but per-emulator we only need to retrigger when the
        // C/C++ sources or the Web wrapper change — Gradle's `inputs.dir` recurses anyway.
        inputs.dir(srcDir).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.dir("$nativeRootDir/native-common").withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.file(File(buildDir, "${emu.output}.wasm"))
        outputs.file(File(buildDir, "${emu.output}.js"))

        val capturedEmsdk = emsdkDir
        val missingMessage = emsdkMissingMessage
        val buildDirLocal = buildDir
        doFirst {
            val emsdk = capturedEmsdk.orNull ?: error(missingMessage)
            require(File(emsdk, "emsdk_env.sh").isFile) {
                "EMSDK=$emsdk does not look like an emsdk checkout (no emsdk_env.sh)."
            }
            require(File(emsdk, "upstream/emscripten/emcc").isFile) {
                "emsdk at $emsdk has no activated compiler (upstream/emscripten/emcc); run " +
                    "`./emsdk install <version> && ./emsdk activate <version>`."
            }
            val envScript = File(emsdk, "emsdk_env.sh").absolutePath
            commandLine(
                "bash",
                "-c",
                "source '$envScript' >/dev/null 2>&1 && emmake make -C '$buildDirLocal' -j",
            )
        }
    }

    buildTask.name
}

val copyEmulatorWasm = tasks.register<Copy>("copyEmulatorWasm") {
    description = "Copies every built emulator WASM blob into apps/js resources so webpack " +
        "bundles them with the JS app (served at /wasm/<name>.{js,wasm} runtime)."
    group = "wasm"
    dependsOn(checkEmsdk)
    dependsOn(buildTaskNames)
    wasmEmulators.forEach { emu ->
        from("$wasmBuildRootDir/${emu.dir}") {
            include("${emu.output}.js", "${emu.output}.wasm")
        }
    }
    into(wasmResourcesDirPath)
    // A Copy that copied nothing would otherwise succeed silently. This is the last line of
    // defence behind `checkEmsdk`: the bundle must receive all nine loader/WASM pairs.
    val expectedFiles = wasmEmulators.flatMap { emu ->
        listOf("${emu.output}.js", "${emu.output}.wasm")
    }
    val targetDir = wasmResourcesDirPath
    doLast {
        val missing = expectedFiles.filterNot { File(targetDir, it).isFile }
        check(missing.isEmpty()) {
            "Emulator WASM copy incomplete in $targetDir; missing: ${missing.joinToString()}"
        }
    }
}

// The bundle's resource pipeline is the one edge that was missing: without it webpack happily
// produced an emulator-less bundle. jsProcessResources feeds the webpack distribution (including
// jsBrowserDistribution and the server's installDist bundle), so every bundle path now builds and
// copies the emulators first. Matching (not named) because the Kotlin/JS plugin registers the task
// after this script is evaluated.
tasks.matching { it.name == "jsProcessResources" }.configureEach {
    dependsOn(copyEmulatorWasm)
}

// Assert the *distribution* (not just the source resources) contains all nine loader/WASM pairs
// plus the app entry points. Runs as a normal Gradle task and mirrors
// scripts/ci/verify-web-bundle.sh, which CI/Docker invoke directly.
tasks.register("verifyWebBundle") {
    description = "Asserts the production web bundle contains index.html, chipbox.js, and all " +
        "nine emulator loader/WASM pairs."
    group = "verification"
    dependsOn("jsBrowserDistribution")
    val distDir = layout.buildDirectory.dir("dist/js/productionExecutable")
    val emulatorCount = wasmEmulators.size
    val requiredFiles = buildList {
        add("index.html")
        add("chipbox.js")
        wasmEmulators.forEach {
            add("wasm/${it.output}.js")
            add("wasm/${it.output}.wasm")
        }
    }
    doLast {
        val dir = distDir.get().asFile
        val missing = requiredFiles.filterNot { File(dir, it).isFile }
        check(missing.isEmpty()) { "Web bundle at $dir is missing: ${missing.joinToString()}" }
        logger.lifecycle("Web bundle OK: index.html + chipbox.js + $emulatorCount loader/WASM pairs.")
    }
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
                // Favorites, playlists, playback history, and the playback session persist in
                // window.localStorage. The browserstate module provides versioned, cross-tab-live
                // Storage-backed stores (the Room/DataStore implementations are JVM/Android-only),
                // wired in WebModules.
                implementation(projects.cbox.common.playlists.api)
                implementation(projects.cbox.common.favorites.api)
                implementation(projects.cbox.common.browserstate)
                // Playback history is stored browser-locally: StoragePlaybackHistoryRepository
                // persists the bounded play log in window.localStorage, fed by the shared
                // (Room-free) RealPlaybackHistoryRecorder from history.real. The Home
                // recently-/most-played cards read straight off that log.
                implementation(projects.cbox.common.history.api)
                implementation(projects.cbox.common.history.real)
                // Session restore/re-save uses the shared RealPlaybackSessionPersister over the
                // browserstate versioned store (localStorage). observe() saves on pause, STOPPED
                // clears, and JsMain flushes snapshotNow() on pagehide/visibilitychange.
                implementation(projects.cbox.common.player.persistence.api)
                implementation(projects.cbox.common.player.persistence.real)
                implementation(projects.cbox.common.player.emulators.api)
                implementation(projects.cbox.common.player.buffer.api)
                implementation(projects.cbox.common.player.buffer.real)
                implementation(projects.cbox.common.player.generator.real)
                implementation(projects.cbox.common.player.cache.api)
                implementation(projects.cbox.common.player.cache.real)
                implementation(projects.cbox.common.player.common.api)
                implementation(projects.cbox.common.player.resampler.api)
                implementation(projects.cbox.common.player.resampler.real)
                implementation(projects.cbox.common.settings.api)
                implementation(projects.cbox.common.settings.real)
                implementation(projects.cbox.common.notif.api)
                implementation(projects.cbox.common.notif.real)
                implementation(projects.cbox.common.crash.api)
                implementation(projects.cbox.common.debug.api)
                implementation(projects.cbox.common.debug.real)
                // sage's `Storage` interface — `LocalStorageStorage` implements it for the
                // browser so the commonMain `RealChipboxSettingsManager` / `RealDebugSettingsManager`
                // drop in unchanged. Persistence is `window.localStorage`.
                implementation(libs.sage.common.storage.common)
                implementation(projects.cbox.common.debugInfo.api)
                implementation(projects.cbox.common.debugInfo.real)
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
                // Repository call out to GET/POST/DELETE against chipbox-server. The same client
                // engine backs Coil's network image fetcher below.
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                // Coil's Ktor3-backed network image fetcher. The default Coil ImageLoader on JS
                // has no fetcher for http(s) URLs, so without this every photo request errors.
                // Configured in `WebImageLoader.kt` together with a Mapper that rewrites the
                // server-side local file paths the scanner stored into `/api/files/by-path`.
                // The `coil` meta artifact carries `SingletonImageLoader` which `JsMain.kt`
                // installs the configured loader into. `cbox.android.images.api` is now a KMP
                // module — its commonMain holds `HatchetCoilLogger` which we route Coil's
                // diagnostics through so image-load failures land in the same console stream
                // as the rest of the app's logging.
                implementation(libs.coil.kt.core)
                implementation(libs.coil.kt.meta)
                implementation(libs.coil.kt.ktor3)
                implementation(projects.cbox.android.images.api)
                // GridImageSize bucket enum — the WebImageLoader Mapper appends ?size=<bucket>
                // to image URLs so the server can serve a pre-resized thumbnail rather than
                // the full-res cover.
                implementation(libs.sage.common.ui.components)

                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui)

                implementation(libs.androidx.lifecycle.viewmodel)
            }
        }
    }
}
