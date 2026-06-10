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

/** Android SDK location: ANDROID_HOME / ANDROID_SDK_ROOT / `sdk.dir` in local.properties. */
fun Project.androidSdkDir(): File {
    System.getenv("ANDROID_HOME")?.let { return File(it) }
    System.getenv("ANDROID_SDK_ROOT")?.let { return File(it) }
    val local = rootProject.file("local.properties")
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
        ?: error("No CMake under ${androidSdkDir()}/cmake — install one: `sdkmanager 'cmake;<ver>'`.")

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

private fun File.hasJniHeaders(): Boolean =
    File(this, "include/jni.h").isFile && File(this, "include/linux/jni_md.h").isFile

/**
 * Resolve a JDK that ships JNI headers (the host native build needs `include/jni.h`). Mirrors
 * apps/jvm: `-Pchipbox.jvm.nativeJdk` override → java.home / JAVA_HOME → `~/.jdks`, `/usr/lib/jvm`.
 */
fun Project.resolveNativeJdk(): File {
    (findProperty("chipbox.jvm.nativeJdk") as? String)?.takeIf { it.isNotBlank() }
        ?.let { return File(it) }
    val candidates = buildList {
        System.getProperty("java.home")?.let { add(File(it)) }
        System.getenv("JAVA_HOME")?.let { add(File(it)) }
        val home = System.getProperty("user.home")
        listOf(File(home, ".jdks"), File("/usr/lib/jvm")).forEach { root ->
            root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }?.let { addAll(it) }
        }
    }
    return candidates.firstOrNull { it.hasJniHeaders() }
        ?: error("No JDK with include/jni.h found; set -Pchipbox.jvm.nativeJdk=/path/to/jdk")
}
