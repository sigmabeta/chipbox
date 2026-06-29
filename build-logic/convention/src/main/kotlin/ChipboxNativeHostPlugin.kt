import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Host (desktop JVM) native build for `apps/jvm`, built through the same shared, cacheable
 * [BuildEmulatorNativeLib] task the Android `:native` modules use — one native build path for both
 * targets. Host = a single "host" variant per emulator, using the host toolchain + JNI headers + a
 * tiny `liblog` shim (psf-family call `__android_log_print`, absent off-device).
 *
 * Exposes:
 *  - directory `build/jvm-native/libs/` with `lib<target>.so` for every emulator, and
 *  - aggregate task **`chipboxHostNativeLibs`** that produces it.
 * `apps/jvm` points its distribution / `java.library.path` wiring at those.
 *
 * Target is [NativeHostTarget]: `LINUX` (default) builds `lib<target>.so` with the host toolchain;
 * `WINDOWS_X64` (`-Pchipbox.jvm.nativeTarget=windows-x64`) cross-compiles `<target>.dll` with MinGW-w64
 * (fully static — no MinGW runtime DLLs to ship — against a Windows JDK's `win32` JNI headers). The
 * CMake/JDK resolvers and the emulator inventory are shared via [NativeEmulators].
 */
class ChipboxNativeHostPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val libsDir = layout.buildDirectory.dir("jvm-native/libs")

            // Skip-gate parity: a no-op aggregate so `apps/jvm` can always wire to it.
            if (providers.gradleProperty("chipbox.skipNative").isPresent) {
                tasks.register("chipboxHostNativeLibs") { group = "native" }
                return
            }

            val target = resolveNativeHostTarget()
            val cmake = resolveCmake()
            val jdk = resolveNativeJdk(target)
            val nativeRoot = rootProject.layout.projectDirectory.dir("cbox/native")
            val shimDir = layout.buildDirectory.dir("jvm-native/hostshim")
            val crossPrefix = target.crossPrefix.orEmpty()
            // Cross targets need a CMake toolchain file (processed before platform/compiler detection)
            // — passing CMAKE_SYSTEM_NAME/compilers as plain -D flags leaves CMake on the host platform
            // and it injects host-only link flags (e.g. -rdynamic) the cross-linker rejects.
            val toolchainFile = nativeRoot.file("cmake/mingw-w64-x86_64.cmake").asFile

            val shim = tasks.register<BuildHostLogShim>("buildHostLogShim") {
                outputDir.set(shimDir)
                compiler.set("${crossPrefix}gcc")
                archiver.set("${crossPrefix}ar")
                // -fPIC matters for the Linux .so; on a Windows DLL all code is already position-
                // independent, so MinGW just warns — omit it there.
                extraCompileArgs.set(if (target == NativeHostTarget.LINUX) listOf("-fPIC") else emptyList())
            }
            val shimDirFile = shimDir.get().asFile
            val cFlags = buildList {
                add("-I$jdk/include")
                add("-I$jdk/include/${target.jniMdSubdir}")
                if (target == NativeHostTarget.LINUX) {
                    // GCC/Linux: PIC for the .so, and blank out the MSVC calling-convention keywords the
                    // Windows-derived cores reference (they don't exist on Linux). On a real Windows target
                    // those ARE live ABI keywords and must stay intact — hence Linux-only.
                    add("-fPIC"); add("-D__fastcall="); add("-D__cdecl="); add("-D__stdcall=")
                }
                add("-I${shimDirFile.absolutePath}/include")
            }.joinToString(" ")
            // Windows: fully static so the .dll carries no MinGW runtime DLLs (libgcc/libstdc++/
            // winpthread/zlib all linked in). Needs a static MinGW zlib in the toolchain (libz.a).
            val linkerFlags = buildString {
                append("-L${shimDirFile.absolutePath}")
                if (target == NativeHostTarget.WINDOWS_X64) append(" -static -static-libgcc -static-libstdc++")
            }

            val perEmulator = NativeEmulators.subdirs.map { emulator ->
                tasks.register<BuildEmulatorNativeLib>("buildHostNative${emulator.replaceFirstChar { it.uppercase() }}") {
                    group = "native"
                    dependsOn(shim)
                    nativeSources.from(nativeRoot.dir(emulator), nativeRoot.dir(NativeEmulators.COMMON_SUBDIR))
                    sourceDir.set(nativeRoot.dir(emulator))
                    cmakeExecutable.set(cmake)
                    // Target-scoped so a Linux build and a Windows cross-build never share a CMake
                    // cache dir (CMAKE_SYSTEM_NAME is sticky in the cache and the toolchain file is only
                    // read on a fresh configure, so a shared dir would pin the first target's platform).
                    workDir.set(layout.buildDirectory.dir("jvm-native/cxx/${target.name.lowercase()}/$emulator"))
                    outputDir.set(layout.buildDirectory.dir("jvm-native/out/${target.name.lowercase()}/$emulator"))
                    cacheKey.put("toolchain", "host")
                    cacheKey.put("target", target.name)
                    cacheKey.put("buildType", "Release")
                    cacheKey.put("emulator", emulator)
                    variantConfigureArgs.put(
                        "host",
                        buildList {
                            if (target == NativeHostTarget.WINDOWS_X64) {
                                add("-DCMAKE_TOOLCHAIN_FILE=${toolchainFile.absolutePath}")
                            }
                            add("-DCMAKE_BUILD_TYPE=Release")
                            add("-DCMAKE_C_FLAGS=$cFlags")
                            add("-DCMAKE_CXX_FLAGS=$cFlags")
                            add("-DCMAKE_SHARED_LINKER_FLAGS=$linkerFlags")
                        },
                    )
                }
            }

            // Sync (not Copy) so the libs dir is an exact mirror of the current target's outputs —
            // switching Linux↔Windows leaves no stale .so next to the .dll (or vice versa).
            tasks.register<Sync>("chipboxHostNativeLibs") {
                group = "native"
                description = "Host-builds every emulator native lib into build/jvm-native/libs/."
                perEmulator.forEach { from(it.flatMap { t -> t.outputDir.dir("host") }) }
                // System.loadLibrary("gme") maps to gme.dll on Windows (no 'lib' prefix), but MinGW
                // emits libgme.dll — strip the prefix so the JVM finds it.
                if (target == NativeHostTarget.WINDOWS_X64) rename("^lib(.*)\\.dll$", "$1.dll")
                into(libsDir)
            }
        }
    }
}

/** Writes + compiles the `liblog` host shim (a no-op `__android_log_print` filtered by priority). */
abstract class BuildHostLogShim @Inject constructor(private val exec: ExecOperations) :
    org.gradle.api.DefaultTask() {

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    /** C compiler for the shim (`gcc`, or the MinGW cross `x86_64-w64-mingw32-gcc`). */
    @get:org.gradle.api.tasks.Input
    abstract val compiler: org.gradle.api.provider.Property<String>

    /** Static archiver (`ar`, or the MinGW cross `x86_64-w64-mingw32-ar`). */
    @get:org.gradle.api.tasks.Input
    abstract val archiver: org.gradle.api.provider.Property<String>

    /** Extra compile flags (e.g. `-fPIC` on Linux; none for the Windows DLL). */
    @get:org.gradle.api.tasks.Input
    abstract val extraCompileArgs: org.gradle.api.provider.ListProperty<String>

    @org.gradle.api.tasks.TaskAction
    fun build() {
        val out = outputDir.get().asFile
        val inc = File(out, "include/android").apply { mkdirs() }
        File(inc, "log.h").writeText(LOG_H)
        val logC = File(out, "log.c").apply { writeText(LOG_C) }
        val logO = File(out, "log.o")
        exec.exec {
            commandLine(
                buildList {
                    add(compiler.get())
                    addAll(extraCompileArgs.get())
                    add("-c"); add(logC.absolutePath); add("-o"); add(logO.absolutePath)
                },
            )
        }
        exec.exec { commandLine(archiver.get(), "rcs", File(out, "liblog.a").absolutePath, logO.absolutePath) }
    }

    private companion object {
        val LOG_H = """
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
        """.trimIndent() + "\n"

        // No-op log filtered by priority (default WARN; override via CHIPBOX_NATIVE_LOG_LEVEL).
        val LOG_C = buildString {
            append("#include <stdarg.h>\n#include <stdio.h>\n#include <stdlib.h>\n")
            append("#include <string.h>\n#include <strings.h>\n")
            append("static int log_min_priority = -1;\n")
            append("static int resolve_min_priority(void) {\n")
            append("    const char *s = getenv(\"CHIPBOX_NATIVE_LOG_LEVEL\");\n")
            append("    if (!s || !*s) return 5;\n")
            append("    if (!strcasecmp(s, \"verbose\")) return 2;\n")
            append("    if (!strcasecmp(s, \"debug\"))   return 3;\n")
            append("    if (!strcasecmp(s, \"info\"))    return 4;\n")
            append("    if (!strcasecmp(s, \"warn\"))    return 5;\n")
            append("    if (!strcasecmp(s, \"error\"))   return 6;\n")
            append("    if (!strcasecmp(s, \"fatal\"))   return 7;\n")
            append("    int n = atoi(s);\n    return (n >= 2 && n <= 7) ? n : 5;\n}\n")
            append("int __android_log_print(int p,const char*t,const char*f,...){\n")
            append("    if (log_min_priority < 0) log_min_priority = resolve_min_priority();\n")
            append("    if (p < log_min_priority) return 0;\n")
            append("    va_list a;va_start(a,f);fprintf(stderr,\"[%s] \",t?t:\"?\");\n")
            append("    int n=vfprintf(stderr,f,a);fputc(10,stderr);va_end(a);return n;\n}\n")
        }
    }
}
