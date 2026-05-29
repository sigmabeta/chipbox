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

val wasmResourcesDirPath: String =
    layout.projectDirectory.dir("src/jsMain/resources/wasm").asFile.absolutePath
val nativeRootDir: String = rootProject.layout.projectDirectory.dir("cbox/native").asFile.absolutePath
val wasmBuildRootDir: String = layout.buildDirectory.dir("wasm").get().asFile.absolutePath

// Each emulator that ships a WASM build is one line here. The CMakeLists in
// `cbox/native/<dir>/` must have an `if(EMSCRIPTEN)` branch that produces a target named
// `chipbox_<output>` (Emscripten emits `.js` + `.wasm` for executable targets). The Kotlin
// side loads each via a `<Capitalized>Wasm.kt` module-loader file. New emulator → one entry
// here, one CMake branch, one `<Name>_Web.cpp` wrapper, one `Wasm<Name>Emulator.kt` subclass.
data class WasmEmulator(val dir: String, val output: String, val displayName: String)
val wasmEmulators = listOf(
    WasmEmulator(dir = "gme", output = "chipbox_gme", displayName = "libgme"),
    WasmEmulator(dir = "vgm", output = "chipbox_vgm", displayName = "libvgm"),
    WasmEmulator(dir = "ssf", output = "chipbox_ssf", displayName = "ssf core"),
    WasmEmulator(dir = "usf", output = "chipbox_usf", displayName = "lazyusf2"),
    WasmEmulator(dir = "psf", output = "chipbox_psf", displayName = "slopsf"),
    WasmEmulator(dir = "ncsf", output = "chipbox_ncsf", displayName = "ncsf"),
    WasmEmulator(dir = "2sf", output = "chipbox_twosf", displayName = "vio2sf"),
    WasmEmulator(dir = "vgmstream", output = "chipbox_vgmstream", displayName = "vgmstream"),
    WasmEmulator(dir = "gba", output = "chipbox_gba", displayName = "mgba"),
)

val buildTaskNames = wasmEmulators.map { emu ->
    val cap = emu.dir.replaceFirstChar { it.uppercaseChar() }
    val srcDir = "$nativeRootDir/${emu.dir}"
    val buildDir = "$wasmBuildRootDir/${emu.dir}"

    val configureTask = tasks.register<Exec>("configure${cap}Wasm") {
        description = "Configures the Emscripten WASM build for ${emu.displayName}. Needs " +
            "\$EMSDK or -Pchipbox.js.emsdk."
        group = "wasm"
        inputs.file(File(srcDir, "CMakeLists.txt"))
        outputs.file(File(buildDir, "CMakeCache.txt"))

        val capturedEmsdk = emsdkDir
        val buildDirLocal = buildDir
        val srcDirLocal = srcDir
        doFirst {
            val emsdk = capturedEmsdk.orNull
                ?: error("Emscripten SDK not found. Install emsdk and set \$EMSDK, or pass " +
                    "-Pchipbox.js.emsdk=/path/to/emsdk.")
            require(File(emsdk, "emsdk_env.sh").isFile) {
                "EMSDK=$emsdk does not look like an emsdk checkout (no emsdk_env.sh)."
            }
            File(buildDirLocal).mkdirs()
            val envScript = File(emsdk, "emsdk_env.sh").absolutePath
            commandLine(
                "bash", "-c",
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
        outputs.file(File(buildDir, "${emu.output}.wasm"))
        outputs.file(File(buildDir, "${emu.output}.js"))

        val capturedEmsdk = emsdkDir
        val buildDirLocal = buildDir
        doFirst {
            val emsdk = capturedEmsdk.orNull
                ?: error("Emscripten SDK not found. Install emsdk and set \$EMSDK, or pass " +
                    "-Pchipbox.js.emsdk=/path/to/emsdk.")
            val envScript = File(emsdk, "emsdk_env.sh").absolutePath
            commandLine(
                "bash", "-c",
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
    dependsOn(buildTaskNames)
    wasmEmulators.forEach { emu ->
        from("$wasmBuildRootDir/${emu.dir}") {
            include("${emu.output}.js", "${emu.output}.wasm")
        }
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

                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui)

                implementation(libs.androidx.lifecycle.viewmodel)
            }
        }
    }
}
