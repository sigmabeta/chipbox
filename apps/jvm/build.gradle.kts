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
File(logCPath).writeText(
    "#include <stdarg.h>\n" +
        "#include <stdio.h>\n" +
        "int __android_log_print(int p,const char*t,const char*f,...)" +
        "{va_list a;va_start(a,f);fprintf(stderr,\"[%s] \",t?t:\"?\");" +
        "int n=vfprintf(stderr,f,a);fputc(10,stderr);va_end(a);return n;}\n",
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

    tasks.register<Exec>("nativeEmulator${capitalTarget}") {
        description = "Host-builds the $target emulator native lib " +
            "(cbox/native/$subdir → apps/jvm/libs/lib$target.so)."
        group = "native"
        dependsOn(configureTask)

        inputs.dir(srcDir).withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.file(outSo)

        commandLine(
            cmakeExecutable,
            "--build", cmakeBuildDirPath,
            "--target", target,
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

// Emit a ready-to-run `java` invocation (no Gradle at runtime). Many modules share a jar
// basename (real.jar/api.jar), which breaks the flat-lib `installDist`; an explicit
// classpath of full, unique jar paths sidesteps that. Writes build/run-standalone.sh.
tasks.register("standaloneScript") {
    val rtFiles: FileCollection = configurations.getByName("runtimeClasspath")
    val jarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val mainClass = application.mainClass
    val libDir = nativeLibsDirFile.absolutePath
    val outFile = layout.buildDirectory.file("run-standalone.sh")
    inputs.files(rtFiles, jarFile)
    outputs.file(outFile)
    doLast {
        val cp = (listOf(jarFile.get().asFile) + rtFiles.files)
            .joinToString(":") { it.absolutePath }
        val f = outFile.get().asFile
        f.writeText(
            "#!/usr/bin/env bash\n" +
                "# Generated by :apps:jvm:standaloneScript — runs without Gradle.\n" +
                "exec java -Djava.library.path='$libDir' \\\n" +
                "  -cp '$cp' \\\n" +
                "  ${mainClass.get()} \"\$@\"\n"
        )
        f.setExecutable(true)
        logger.lifecycle("Wrote ${f.absolutePath}")
    }
}

dependencies {
    implementation(projects.cbox.android.player.generator.real)
    implementation(projects.cbox.android.player.emulators.gba.real)
    implementation(projects.cbox.android.player.emulators.gme.real)
    implementation(projects.cbox.android.player.emulators.psf.real)
    implementation(projects.cbox.android.player.emulators.ssf.real)
    implementation(projects.cbox.android.player.emulators.twosf.real)
    implementation(projects.cbox.android.player.emulators.usf.real)
    implementation(projects.cbox.android.player.emulators.vgm.real)
    implementation(projects.cbox.common.contentsource.api)
    implementation(projects.cbox.common.strings.api)
    implementation(projects.cbox.common.debug.api)
    implementation(projects.cbox.common.debug.real)
    implementation(projects.cbox.common.settings.api)
    implementation(projects.cbox.common.settings.real)
    // The JVM target now reuses the same `ChipboxAppUi` composable as Android — see
    // `cbox/android/appui/api` (now sage.kmp). `appui:api` already pulls in every feature
    // module via `api(...)`, so we don't list them individually here.
    implementation(projects.cbox.android.appui.api)

    // Direct deps for things the JVM-side Metro graph (JvmChipboxGraph) needs that aren't
    // covered transitively by appui.
    implementation(projects.features.settings.real)
    implementation(projects.features.library.real)
    implementation(projects.cbox.android.ui.list.api)
    implementation(projects.cbox.android.ui.chrome.api)
    // RealDirector — needed by every feature VM that depends on player.director.api
    // (NowPlaying, GameDetail, ArtistDetail, GamesForPlatform, BrowseAllTracks). All wired
    // into the appui module, so without a Director provider the JvmChipboxGraph fails to
    // resolve them.
    implementation(projects.cbox.common.player.director.real)
    // DirectorModule's `@ContributesTo(AppScope) @Provides` binds Director → RealDirector
    // for the Metro graph; without :di the binding isn't on the classpath and feature VMs
    // that take a Director fail to resolve.
    implementation(projects.cbox.common.player.director.di)
    implementation(projects.cbox.common.player.speaker.fake)
    implementation(projects.cbox.common.player.buffer.real)
    implementation(projects.cbox.common.player.common.api)
    implementation(projects.cbox.common.repository.api)
    implementation(projects.cbox.common.models.api)

    // Room KMP database used as the JVM target's real library (replaces the
    // SingleTrackRepository shim for `scan` / `play` modes). `sqlite-bundled`
    // is the cross-platform Room driver Android doesn't need.
    implementation(projects.cbox.android.repository.real)
    implementation(projects.cbox.android.database.all)
    implementation(libs.sqlite.bundled)

    // Shared scanner — uses the new LibrarySource interface; LocalFileContentSource
    // is the JVM impl, the Android twin is AndroidFileContentSource (SAF).
    implementation(projects.cbox.android.scanner.real)
    implementation(projects.cbox.common.readers.api)

    // Metro DI — JvmChipboxGraph is the runtime DI root (replaces the plain-Dagger
    // JvmChipboxComponent dropped at M6). AppScope lives in sage/common/di;
    // metrox-viewmodel(-compose) backs metroViewModel<T>() on the desktop UI.
    implementation(libs.sage.common.di)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)

    implementation(libs.sage.common.logging)
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
