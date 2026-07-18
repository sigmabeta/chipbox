import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * Shared facts about the vendored emulator native builds under `cbox/native/<subdir>`, used by both
 * the Android `:native` modules ([ChipboxEmulatorNativePlugin]) and the `apps/jvm` host build.
 * Single source of truth so the two native build paths can't drift.
 */
object NativeEmulators {
    /** Emulator source subdirs under cbox/native. The CMake *target* names differ for a couple
     *  (psf → slopsf, 2sf → twosf), but the build produces `lib<target>.so` regardless — both
     *  paths build the default target(s) and collect whatever `.so` lands, so Gradle needn't
     *  know the target names. */
    val subdirs = listOf("gme", "psf", "ssf", "usf", "2sf", "vgm", "gba", "ncsf", "vgmstream")

    /** Header-only JNI bridge shared by every emulator (referenced via add_subdirectory). */
    const val COMMON_SUBDIR = "native-common"

    const val NDK_VERSION = "29.0.14206865"
    // No pinned CMake version: the native builds use the highest CMake under $SDK/cmake (see
    // resolveSdkCmake / resolveCmake). The .so is determined by NDK_VERSION + explicit flags.
    const val ANDROID_PLATFORM = "android-26"
}

/**
 * Desktop-JVM host native target. The default (`host`) detects the build machine's OS — `LINUX`
 * (`lib<t>.so`) or `MACOS` (`lib<t>.dylib`), each built natively with the host toolchain. `WINDOWS_X64`
 * cross-compiles Windows `<t>.dll`s via the MinGW-w64 toolchain (`-Pchipbox.jvm.nativeTarget=windows-x64`,
 * from a Linux build machine — jpackage then packages them on windows-latest).
 */
enum class NativeHostTarget(
    /** The JDK `include/<subdir>/jni_md.h` (per-OS JNI machine-dependent header) for this target. */
    val jniMdSubdir: String,
    /** MinGW-style cross-compiler prefix (e.g. `x86_64-w64-mingw32-`), or null when native to host. */
    val crossPrefix: String?,
) {
    LINUX("linux", null),
    MACOS("darwin", null),
    WINDOWS_X64("win32", "x86_64-w64-mingw32-"),
}

/**
 * Host native target from `-Pchipbox.jvm.nativeTarget`. Unset/`host` detects the build OS (LINUX or
 * MACOS — each native); `linux`/`macos` force one; `windows-x64` selects the MinGW cross-compile.
 */
fun Project.resolveNativeHostTarget(): NativeHostTarget {
    val raw = (findProperty("chipbox.jvm.nativeTarget") as? String)?.trim()?.lowercase()
    return when (raw) {
        "linux" -> NativeHostTarget.LINUX
        "macos", "mac", "osx", "darwin" -> NativeHostTarget.MACOS
        "windows-x64", "windows", "win", "mingw" -> NativeHostTarget.WINDOWS_X64
        null, "", "host" -> {
            val os = org.gradle.internal.os.OperatingSystem.current()
            when {
                os.isMacOsX -> NativeHostTarget.MACOS
                os.isLinux -> NativeHostTarget.LINUX
                else -> error(
                    "No native host build for ${os.name}; cross-compile with " +
                        "-Pchipbox.jvm.nativeTarget=windows-x64 (from Linux).",
                )
            }
        }
        else -> error("Unknown -Pchipbox.jvm.nativeTarget='$raw'; use 'host', 'linux', 'macos', or 'windows-x64'.")
    }
}

/** Android SDK location: ANDROID_HOME / ANDROID_SDK_ROOT / `sdk.dir` in local.properties. */
fun Project.androidSdkDir(): File {
    System.getenv("ANDROID_HOME")?.let { return File(it) }
    System.getenv("ANDROID_SDK_ROOT")?.let { return File(it) }
    // isolated.rootProject keeps this root-relative lookup Isolated-Projects-safe (a plain
    // rootProject.file access reaches into another project's state, which IP forbids).
    val local = isolated.rootProject.projectDirectory.file("local.properties").asFile
    if (local.isFile) {
        val props = Properties().apply { local.inputStream().use { load(it) } }
        props.getProperty("sdk.dir")?.let { return File(it) }
    }
    error("Android SDK not found: set ANDROID_HOME or sdk.dir in local.properties")
}

/**
 * Highest CMake version directory under `$SDK/cmake` with an executable `bin/cmake`, or null. Names
 * sort lexicographically, which is correct for the SDK's `3.22.1` / `4.1.x` scheme.
 */
private fun Project.highestSdkCmakeDir(): File? =
    File(androidSdkDir(), "cmake").listFiles()
        ?.filter { it.isDirectory && File(it, "bin/cmake").canExecute() }
        ?.maxByOrNull { it.name }

/**
 * CMake for the Android `:native` builds: they configure with `-G Ninja`, so they need the SDK's
 * CMake (it ships a sibling `ninja`; a bare `$PATH` cmake wouldn't). Uses the highest version under
 * `$SDK/cmake` — cmake 4.1.x in recent cimg/android images, or whatever a contributor has locally.
 * No version pin: the `.so` is determined by the NDK toolchain + the explicit configure flags, not
 * the CMake version. (cmake 4 needs the source's cmake_minimum_required floor to be >= 3.5.)
 */
fun Project.resolveSdkCmake(): File =
    highestSdkCmakeDir()?.let { File(it, "bin/cmake") }
        // No SDK CMake installed. This runs at *configuration* time in every job that configures the
        // native modules — including base-image CI jobs (no SDK cmake) that never *execute* the native
        // task. So don't fail here: return a placeholder. The task only runs where an SDK cmake exists
        // (the `-ndk` image's 4.1.x on CI, a contributor's local install), where the branch above hits.
        ?: File(androidSdkDir(), "cmake/none/bin/cmake")

/**
 * CMake for the apps/jvm host build: `-Pchipbox.cmake` override → highest SDK CMake → `$PATH` →
 * bare `cmake`. Prefers the SDK copy so CI deterministically uses the image's CMake.
 */
fun Project.resolveCmake(): File {
    (findProperty("chipbox.cmake") as? String)?.takeIf { it.isNotBlank() }?.let { return File(it) }
    highestSdkCmakeDir()?.let { return File(it, "bin/cmake") }
    System.getenv("PATH").orEmpty()
        .splitToSequence(File.pathSeparatorChar)
        .filter { it.isNotEmpty() }
        .map { File(it, "cmake") }
        .firstOrNull { it.canExecute() }
        ?.let { return it }
    return File("cmake")
}

private fun File.hasJniHeaders(target: NativeHostTarget): Boolean =
    File(this, "include/jni.h").isFile && File(this, "include/${target.jniMdSubdir}/jni_md.h").isFile

/**
 * Resolve a JDK that ships the JNI headers the host native build compiles against — `include/jni.h`
 * plus the target's `include/<subdir>/jni_md.h`. `-Pchipbox.jvm.nativeJdk` override → java.home /
 * JAVA_HOME → `~/.jdks`, `/usr/lib/jvm`. Cross targets (Windows) can't be auto-discovered — the local
 * machine's JDKs ship the *host* `jni_md.h`, not the target's — so they require the explicit override
 * pointing at a JDK for the target OS (only its `include/<subdir>/jni_md.h` is read; no code runs).
 */
fun Project.resolveNativeJdk(target: NativeHostTarget = NativeHostTarget.LINUX): File {
    (findProperty("chipbox.jvm.nativeJdk") as? String)?.takeIf { it.isNotBlank() }?.let {
        val jdk = File(it)
        require(jdk.hasJniHeaders(target)) {
            "-Pchipbox.jvm.nativeJdk=$it is missing include/${target.jniMdSubdir}/jni_md.h; " +
                "point it at a JDK for ${target.name}."
        }
        return jdk
    }
    if (target.crossPrefix != null) {
        error(
            "Cross-compiling to ${target.name} needs a target JDK: set -Pchipbox.jvm.nativeJdk=/path/to/jdk " +
                "whose include/${target.jniMdSubdir}/jni_md.h exists (e.g. a Windows JDK).",
        )
    }
    val candidates = buildList {
        System.getProperty("java.home")?.let { add(File(it)) }
        System.getenv("JAVA_HOME")?.let { add(File(it)) }
        val home = System.getProperty("user.home")
        listOf(File(home, ".jdks"), File("/usr/lib/jvm")).forEach { root ->
            root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }?.let { addAll(it) }
        }
    }
    return candidates.firstOrNull { it.hasJniHeaders(target) }
        ?: error("No JDK with include/jni.h found; set -Pchipbox.jvm.nativeJdk=/path/to/jdk")
}
