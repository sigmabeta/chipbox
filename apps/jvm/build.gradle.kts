plugins {
    alias(libs.plugins.sage.jvm)
    alias(libs.plugins.metro)
    // Compose Multiplatform desktop. Two plugins are needed: the Kotlin Compose compiler
    // (shared with the Android UI) handles @Composable codegen, and the JetBrains Compose
    // plugin provides the `compose.desktop.currentOs` dependency notation that resolves the
    // per-OS Skia native artifact. Both must be applied here; only the compiler plugin will
    // travel through the future sage.compose.kmp convention for shared UI modules.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    application
}

application {
    mainClass.set("net.sigmabeta.chipbox.jvm.MainKt")
    applicationName = "chipbox"
}

// Native emulator libs are host-built (CMake/host C-C++ toolchain, not the NDK) into
// `apps/jvm/libs/` and loaded at runtime via `System.loadLibrary("gme")`. The Android target
// gets the same `.so`s for free from AGP's `externalNativeBuild { cmake { … } }` per `:native`
// module; the JVM target has no such DSL, so the tasks below replicate the same CMake
// invocations the README documents (kept in sync intentionally — same flags, same shim).
//
// Linux-only for now. macOS/Windows would need their own toolchain probing; out of scope.
val nativeLibsDirFile: File = layout.projectDirectory.dir("libs").asFile
val nativeBuildDirFile: File = layout.projectDirectory.dir("native-build").asFile
val hostShimDirFile: File = File(nativeBuildDirFile, "hostshim")

// Resolve cmake — explicit `-Pchipbox.jvm.cmake=…` first, then `$PATH` (split manually to
// avoid `providers.exec("which", …)` failing the configuration cache when which returns 1),
// then the Android SDK's bundled copy (which any contributor targeting Android already has).
// Defaults to bare "cmake" if everything fails so the task error message points at PATH.
val cmakeExecutable: String = run {
    val override = (findProperty("chipbox.jvm.cmake") as? String)?.takeIf { it.isNotBlank() }
    val pathCmake = System.getenv("PATH").orEmpty()
        .splitToSequence(File.pathSeparatorChar)
        .filter { it.isNotEmpty() }
        .map { File(it, "cmake") }
        .firstOrNull { it.canExecute() }
        ?.absolutePath
    val androidSdk = listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        "${System.getProperty("user.home")}/Android/Sdk",
        "${System.getProperty("user.home")}/apps/android-sdk",
    ).firstNotNullOfOrNull { sdkRoot ->
        val cmakeRoot = file("$sdkRoot/cmake")
        if (!cmakeRoot.isDirectory) return@firstNotNullOfOrNull null
        cmakeRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.firstNotNullOfOrNull { dir ->
                file("${dir.absolutePath}/bin/cmake").takeIf { it.canExecute() }?.absolutePath
            }
    }
    override ?: pathCmake ?: androidSdk ?: "cmake"
}

// JDK with `include/jni.h` + `include/linux/jni_md.h`. Gradle's own java.home is often a JBR
// (Android Studio bundle) or a JRE that ships no JNI headers — searching `~/.jdks` + a few
// common install roots covers IntelliJ-managed JDKs and the typical /usr/lib/jvm layout.
// Override via `-Pchipbox.jvm.nativeJdk=/path/to/jdk` (must contain include/jni.h +
// include/linux/jni_md.h). Defaults to Gradle's java.home if nothing matches, so the actual
// task failure ("no include/jni.h") still points at a concrete path.
val jdkHome: String = run {
    val override = (findProperty("chipbox.jvm.nativeJdk") as? String)?.takeIf { it.isNotBlank() }
    fun hasJniHeaders(path: String): Boolean =
        File("$path/include/jni.h").isFile && File("$path/include/linux/jni_md.h").isFile
    val candidates = sequence {
        override?.let { yield(it) }
        yield(System.getProperty("java.home"))
        System.getenv("JAVA_HOME")?.let { yield(it) }
        val userHome = System.getProperty("user.home")
        listOf("$userHome/.jdks", "/usr/lib/jvm").forEach { root ->
            File(root).listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.forEach { yield(it.absolutePath) }
        }
    }
    candidates.firstOrNull { hasJniHeaders(it) } ?: System.getProperty("java.home")
}

// (cbox/native subdir, CMake target name → `lib<target>.so`). The pairing is asymmetric for
// psf (target = `slopsf` produces `libslopsf.so`) and 2sf (target = `twosf` → `libtwosf.so`);
// keep both columns explicit to mirror the README's `build` invocations.
val nativeEmulators: List<Pair<String, String>> = listOf(
    "gme" to "gme",
    "psf" to "slopsf",
    "ssf" to "ssf",
    "usf" to "usf",
    "2sf" to "twosf",
    "vgm" to "vgm",
    "gba" to "gba",
    "ncsf" to "ncsf",
    "vgmstream" to "vgmstream",
)

// One-time android/log shim — psf's debug probes call `__android_log_print`, which doesn't
// exist on a host JVM. We emit a header + a tiny static lib that no-op-prints to stderr; psf
// links against `liblog.a` via `-L$SHIM`. Three small tasks (write sources → compile .o →
// archive .a) so each step has explicit inputs/outputs and incremental builds stay sharp.
val logCFile = File(hostShimDirFile, "log.c")
val logOFile = File(hostShimDirFile, "log.o")
val logAFile = File(hostShimDirFile, "liblog.a")
val logHeaderFile = File(hostShimDirFile, "include/android/log.h")

// Pre-resolve absolute paths so the task action lambdas don't close over `File` instances
// (which the configuration cache rejects as "Gradle script object references"). The doLast
// for the hostshim task shells out via ProcessBuilder rather than `Exec` to keep everything
// in one task — we want exactly the static .a + header on disk; the intermediate .o is
// implementation detail and Gradle's incremental check on the two outputs is sufficient.
val logCPath = logCFile.absolutePath
val logOPath = logOFile.absolutePath
val logAPath = logAFile.absolutePath
val logHeaderPath = logHeaderFile.absolutePath
val logHeaderParentPath = logHeaderFile.parentFile.absolutePath

// Hostshim sources (android/log.h + log.c) are static content with no inputs, so emit them
// at configuration time rather than inside a task — a `doLast` lambda would implicitly
// capture build-script references that the Gradle configuration cache rejects as
// "script object references", and the content here is tiny enough that "rewrite every
// configuration" is cheaper than the workarounds (custom Task type, .kt file under
// buildSrc, etc).
File(logHeaderParentPath).mkdirs()
File(logHeaderPath).writeText(
    """
    #ifndef _HOSTSHIM_ANDROID_LOG_H
    #define _HOSTSHIM_ANDROID_LOG_H
    #ifdef __cplusplus
    extern "C" {
    #endif
    enum { ANDROID_LOG_VERBOSE=2, ANDROID_LOG_DEBUG, ANDROID_LOG_INFO,
           ANDROID_LOG_WARN, ANDROID_LOG_ERROR, ANDROID_LOG_FATAL };
    int __android_log_print(int prio, const char *tag, const char *fmt, ...);
    #ifdef __cplusplus
    }
    #endif
    #endif
    """.trimIndent() + "\n",
)
// Filter by priority before printing — without this, mGBA's `GBA DMA:` /
// `GBA BIOS: SWI:` trace lines (ANDROID_LOG_DEBUG=3) and similar from other
// emulators spam stderr on every track. Android's logd does the equivalent
// filtering for the NDK build; on host we do it here. Defaults to WARN (5);
// override at runtime via `CHIPBOX_NATIVE_LOG_LEVEL` (numeric 2-7, or one
// of verbose/debug/info/warn/error/fatal) when chasing an emulator-level bug.
File(logCPath).writeText(
    "#include <stdarg.h>\n" +
        "#include <stdio.h>\n" +
        "#include <stdlib.h>\n" +
        "#include <string.h>\n" +
        "#include <strings.h>\n" +
        "static int log_min_priority = -1;\n" +
        "static int resolve_min_priority(void) {\n" +
        "    const char *s = getenv(\"CHIPBOX_NATIVE_LOG_LEVEL\");\n" +
        "    if (!s || !*s) return 5;\n" +
        "    if (!strcasecmp(s, \"verbose\")) return 2;\n" +
        "    if (!strcasecmp(s, \"debug\"))   return 3;\n" +
        "    if (!strcasecmp(s, \"info\"))    return 4;\n" +
        "    if (!strcasecmp(s, \"warn\"))    return 5;\n" +
        "    if (!strcasecmp(s, \"error\"))   return 6;\n" +
        "    if (!strcasecmp(s, \"fatal\"))   return 7;\n" +
        "    int n = atoi(s);\n" +
        "    return (n >= 2 && n <= 7) ? n : 5;\n" +
        "}\n" +
        "int __android_log_print(int p,const char*t,const char*f,...){\n" +
        "    if (log_min_priority < 0) log_min_priority = resolve_min_priority();\n" +
        "    if (p < log_min_priority) return 0;\n" +
        "    va_list a;va_start(a,f);fprintf(stderr,\"[%s] \",t?t:\"?\");\n" +
        "    int n=vfprintf(stderr,f,a);fputc(10,stderr);va_end(a);return n;\n" +
        "}\n",
)

val hostShimCompile = tasks.register<Exec>("nativeHostShimCompile") {
    description = "Compiles the hostshim log.c → log.o."
    group = "native"
    inputs.file(logCFile)
    outputs.file(logOFile)
    commandLine("gcc", "-fPIC", "-c", logCFile.absolutePath, "-o", logOFile.absolutePath)
}

val hostShimTask = tasks.register<Exec>("nativeHostShim") {
    description = "Archives the hostshim log.o → liblog.a (psf links against this)."
    group = "native"
    dependsOn(hostShimCompile)
    inputs.file(logOFile)
    outputs.file(logAFile)
    commandLine("ar", "rcs", logAFile.absolutePath, logOFile.absolutePath)
}

// Per-emulator: configure + build into apps/jvm/libs/lib<target>.so. Two `Exec` tasks per
// emulator (cmake configure + cmake build) so Gradle's task graph models the dependency edge
// directly. Inputs are the CMake tree under cbox/native/<subdir>; the build task's output is
// the produced .so so unchanged sources skip the rebuild.
val nativeLibsDirPath = nativeLibsDirFile.absolutePath
val hostShimIncludePath = "${hostShimDirFile.absolutePath}/include"
val hostShimLinkerFlag = "-L${hostShimDirFile.absolutePath}"

val nativeEmulatorTasks = nativeEmulators.map { (subdir, target) ->
    val capitalTarget = target.replaceFirstChar { it.uppercase() }
    val srcDir = rootProject.file("cbox/native/$subdir")
    val cmakeBuildDir = File(nativeBuildDirFile, subdir)
    val outSo = File(nativeLibsDirFile, "lib$target.so")
    val srcDirPath = srcDir.absolutePath
    val cmakeBuildDirPath = cmakeBuildDir.absolutePath

    val hostDef = listOf(
        "-D__fastcall=",
        "-D__cdecl=",
        "-D__stdcall=",
        "-I$hostShimIncludePath",
    ).joinToString(" ")
    val cFlags = "-I$jdkHome/include -I$jdkHome/include/linux -fPIC $hostDef"

    val configureTask = tasks.register<Exec>("nativeEmulator${capitalTarget}Configure") {
        description = "CMake configure for the $target emulator (cbox/native/$subdir)."
        group = "native"
        dependsOn(hostShimTask)

        inputs.dir(srcDir).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("jdkHome", jdkHome)
        inputs.property("cmakeExecutable", cmakeExecutable)
        outputs.file(File(cmakeBuildDir, "CMakeCache.txt"))

        // Use `Task#doFirst` with paths-as-strings rather than capturing `File` objects from
        // the build script — the configuration cache rejects closures over script-object
        // refs but is fine with String captures. Same goes for the JNI-header check: it
        // belongs at task execution time, not configuration time (otherwise it fires for
        // every Gradle invocation in this module, blocking unrelated tasks).
        val makeBuildDirPath = cmakeBuildDirPath
        val makeLibsDirPath = nativeLibsDirPath
        val capturedJdkHome = jdkHome
        doFirst {
            require(File("$capturedJdkHome/include/jni.h").isFile) {
                "Resolved JDK at $capturedJdkHome has no include/jni.h. The native emulator " +
                    "libs need JNI headers (a JRE or stripped JDK won't work). Override with " +
                    "`-Pchipbox.jvm.nativeJdk=/path/to/jdk` (must contain include/jni.h)."
            }
            File(makeBuildDirPath).mkdirs()
            File(makeLibsDirPath).mkdirs()
        }

        commandLine(
            cmakeExecutable,
            "-S", srcDirPath,
            "-B", cmakeBuildDirPath,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DCMAKE_C_FLAGS=$cFlags",
            "-DCMAKE_CXX_FLAGS=$cFlags",
            "-DCMAKE_SHARED_LINKER_FLAGS=$hostShimLinkerFlag",
            "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=$nativeLibsDirPath",
        )
    }

    tasks.register<Exec>("nativeEmulator$capitalTarget") {
        description = "Host-builds the $target emulator native lib " +
            "(cbox/native/$subdir → apps/jvm/libs/lib$target.so)."
        group = "native"
        dependsOn(configureTask)

        inputs.dir(srcDir).withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.file(outSo)

        commandLine(
            cmakeExecutable,
            "--build",
            cmakeBuildDirPath,
            "--target",
            target,
            "-j",
        )
    }
}

// Aggregate — `:apps:jvm:nativeLibs` builds all 7 emulator libraries.
val nativeLibsTask = tasks.register("nativeLibs") {
    description = "Host-builds every emulator native lib into apps/jvm/libs/."
    group = "native"
    dependsOn(nativeEmulatorTasks)
}

tasks.named<JavaExec>("run") {
    dependsOn(nativeLibsTask)
    systemProperty("java.library.path", nativeLibsDirFile.absolutePath)
}

// Bundle the emulator .so/.dylib/.dll files into `lib/native/` inside the distribution so
// `applicationDefaultJvmArgs`' `__APP_HOME__/lib/native` resolves at launch. `installDist`,
// `distZip`, and `distTar` all consume this. Path-derived archive names from the SAGE
// convention plugins (`sage.kmp` / `sage.jvm`) keep the per-module jars unique in `lib/`,
// so the flat distribution layout works without the old `standaloneScript` workaround.
distributions {
    named("main") {
        contents {
            from(nativeLibsDirFile) {
                into("lib/native")
            }
        }
    }
}
listOf("installDist", "distZip", "distTar").forEach { taskName ->
    tasks.named(taskName) { dependsOn(nativeLibsTask) }
}

// Inject `-Djava.library.path=…/lib/native` directly into the exec line of each generated
// start script. Routing through `applicationDefaultJvmArgs` doesn't work for native paths:
// Gradle's unix template puts JVM args through an xargs+sed escape pipeline that turns `$`
// into `\$`, so the shell never expands `$APP_HOME` and the JVM ends up looking for a
// literal "APP_HOME" directory. Injecting after the exec line is the only place where the
// shells (bash `$APP_HOME`, cmd `%APP_HOME%`) actually interpolate the install root.
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        unixScript.writeText(
            unixScript.readText().replace(
                "exec \"\$JAVACMD\" \"\$@\"",
                "exec \"\$JAVACMD\" \"-Djava.library.path=\$APP_HOME/lib/native\" \"\$@\"",
            )
        )
        // The bat template's exec line ends in `%CMD_LINE_ARGS%` (Gradle ≤ 8.x) or `%*`
        // (current). Match the `-classpath` segment instead — stable across both — and
        // splice the system property in just before it.
        windowsScript.writeText(
            windowsScript.readText().replace(
                "-classpath \"%CLASSPATH%\"",
                "-D\"java.library.path=%APP_HOME%\\lib\\native\" -classpath \"%CLASSPATH%\"",
            )
        )
    }
}

dependencies {
    implementation(projects.cbox.common.player.generator.real)
    implementation(projects.cbox.common.player.emulators.gba.real)
    implementation(projects.cbox.common.player.emulators.gme.real)
    implementation(projects.cbox.common.player.emulators.ncsf.real)
    implementation(projects.cbox.common.player.emulators.psf.real)
    implementation(projects.cbox.common.player.emulators.ssf.real)
    implementation(projects.cbox.common.player.emulators.twosf.real)
    implementation(projects.cbox.common.player.emulators.usf.real)
    implementation(projects.cbox.common.player.emulators.vgm.real)
    implementation(projects.cbox.common.player.emulators.vgmstream.real)
    implementation(projects.cbox.common.contentsource.api)
    implementation(projects.cbox.common.contentsource.file.real)
    implementation(projects.cbox.common.strings.real)
    implementation(projects.cbox.common.debug.api)
    implementation(projects.cbox.common.debug.real)
    implementation(projects.cbox.common.settings.api)
    implementation(projects.cbox.common.settings.real)
    // The JVM target now reuses the same `ChipboxAppUi` composable as Android — see
    // `cbox/android/appui/api` (now sage.kmp). `appui:api` already pulls in every feature
    // module via `api(...)`, so we don't list them individually here.
    implementation(projects.cbox.common.appui.api)

    // Direct deps for things the JVM-side Metro graph (JvmChipboxGraph) needs that aren't
    // covered transitively by appui.
    implementation(projects.features.settings.real)
    implementation(projects.features.library.real)
    implementation(projects.cbox.common.ui.list.api)
    implementation(projects.cbox.common.ui.chrome.api)
    // RealDirector — needed by every feature VM that depends on player.director.api
    // (NowPlaying, GameDetail, ArtistDetail, GamesForPlatform, BrowseAllTracks). All wired
    // into the appui module, so without a Director provider the JvmChipboxGraph fails to
    // resolve them.
    implementation(projects.cbox.common.player.director.real)
    // DirectorModule's `@ContributesTo(AppScope) @Provides` binds Director → RealDirector
    // for the Metro graph; without :di the binding isn't on the classpath and feature VMs
    // that take a Director fail to resolve.
    implementation(projects.cbox.common.player.director.di)
    // SourceDataLineSpeaker (the JVM/desktop Speaker) now lives in the shared player:speaker:real
    // module's jvmMain alongside the Android AudioTrack impl in androidMain.
    implementation(projects.cbox.common.player.speaker.real)
    implementation(projects.cbox.common.player.speaker.api)
    implementation(projects.cbox.common.player.buffer.real)
    implementation(projects.cbox.common.player.common.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.models.api)
    // appDataDir() — resolves the standardized per-OS data directory under the user's home.
    implementation(projects.cbox.common.utils.api)
    // DebugInfoModule's `@ContributesTo(AppScope) @Provides` binds DebugInfoManager →
    // RealDebugInfoManager(director, generator, speaker, bufferDebugSource, scope), reached by
    // the PlaybackStatusViewModel now wired into the shared shell. `:di` is sage.di.jvm (the
    // Android app pulls the same module), so no variant-resolution trouble on the JVM classpath.
    // BufferDebugSource + CoroutineScope are supplied by JvmModules.kt.
    implementation(projects.cbox.common.debugInfo.di)

    // Room KMP database used as the JVM target's real library. `sqlite-bundled` is the
    // cross-platform Room driver Android doesn't need.
    implementation(projects.cbox.common.repository.real)
    implementation(projects.cbox.common.database.real)
    implementation(libs.sqlite.bundled)

    // Shared scanner — uses the new LibrarySource interface; LocalFileContentSource
    // is the JVM impl, the Android twin is AndroidFileContentSource (SAF).
    implementation(projects.cbox.common.scanner.real)
    implementation(projects.cbox.common.readers.api)

    // Metro DI — JvmChipboxGraph is the runtime DI root (replaces the plain-Dagger
    // JvmChipboxComponent dropped at M6). AppScope lives in sage/common/di;
    // metrox-viewmodel(-compose) backs metroViewModel<T>() on the desktop UI.
    implementation(libs.sage.common.di)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)

    implementation(libs.sage.common.logging)
    // LocalLogger composition local (provided in DesktopMain) lives here.
    implementation(libs.sage.common.ui.perfCompose)
    implementation(libs.sage.common.appinfo)
    implementation(libs.sage.common.storage.common)
    implementation(libs.kotlinx.coroutines.core)
    // Dispatchers.Main on the JVM target = AWT event queue. SettingsViewModel and any other
    // ChipboxListViewModel scheduling onto viewModelScope (default = Main) needs this runtime.
    implementation(libs.kotlinx.coroutines.swing)

    // Compose Multiplatform desktop. The platform-agnostic libs come from the catalog so
    // shared UI modules (future sage.compose.kmp) pin the exact same versions; the per-OS
    // Skia native is pulled via `compose.desktop.currentOs` from the JetBrains plugin
    // extension applied above. Bootstrap inlines its Hello composable in DesktopMain.kt;
    // these deps will mostly move into shared modules in follow-up slices.
    implementation(libs.jetbrains.compose.runtime)
    implementation(libs.jetbrains.compose.foundation)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.ui)
    implementation(compose.desktop.currentOs)

    // Shared Chipbox color schemes + typography + ChipboxTheme — same palette / type structure
    // the Android UI uses via cbox/android/ui/theme/api. Transitively pulls
    // cbox/common/ui/fonts/api (via api()), so the JVM target gets real Chipbox pixel-art
    // fonts via Compose Multiplatform resources.
    implementation(projects.cbox.common.ui.theme.api)

    // androidx.lifecycle.ViewModel for HelloViewModel (Metro VMs extend ViewModel directly
    // post-M6 — the old ChipboxViewModel marker class in cbox/common/ui/vm/api was for the
    // pre-Metro chipboxViewModel<T>() multiplatform machinery, now deleted).
    implementation(libs.androidx.lifecycle.viewmodel)

    // Voyager — Compose Multiplatform navigation. Replaces what would otherwise be
    // androidx.navigation:navigation-compose (the AndroidX CMP fork publishes JVM stubs
    // only, no runtime). Each screen implements `Screen`; `Navigator(MyScreen())` owns
    // the back stack.
    implementation(libs.voyager.navigator)
}
