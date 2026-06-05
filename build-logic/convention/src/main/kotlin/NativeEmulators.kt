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
    const val CMAKE_VERSION = "3.22.1"
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
 * Resolve a CMake binary, mirroring apps/jvm's logic: `-Pchipbox.cmake` override → `$PATH` →
 * highest version under `$SDK/cmake` (the copy any Android contributor already has) → bare `cmake`.
 */
fun Project.resolveCmake(): File {
    (findProperty("chipbox.cmake") as? String)?.takeIf { it.isNotBlank() }?.let { return File(it) }
    System.getenv("PATH").orEmpty()
        .splitToSequence(File.pathSeparatorChar)
        .filter { it.isNotEmpty() }
        .map { File(it, "cmake") }
        .firstOrNull { it.canExecute() }
        ?.let { return it }
    File(androidSdkDir(), "cmake").listFiles()
        ?.filter { it.isDirectory }
        ?.sortedByDescending { it.name }
        ?.forEach { dir -> File(dir, "bin/cmake").takeIf { it.canExecute() }?.let { return it } }
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
