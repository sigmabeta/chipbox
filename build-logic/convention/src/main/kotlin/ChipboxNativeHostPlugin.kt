import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
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
 * Linux-only for now (matches the previous inline build). The CMake/JDK resolvers and the emulator
 * inventory are shared via [NativeEmulators].
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

            val cmake = resolveCmake()
            val jdk = resolveNativeJdk()
            val nativeRoot = rootProject.layout.projectDirectory.dir("cbox/native")
            val shimDir = layout.buildDirectory.dir("jvm-native/hostshim")

            val shim = tasks.register<BuildHostLogShim>("buildHostLogShim") {
                outputDir.set(shimDir)
            }
            val shimDirFile = shimDir.get().asFile
            val cFlags = listOf(
                "-I$jdk/include", "-I$jdk/include/linux", "-fPIC",
                "-D__fastcall=", "-D__cdecl=", "-D__stdcall=",
                "-I${shimDirFile.absolutePath}/include",
            ).joinToString(" ")

            val perEmulator = NativeEmulators.subdirs.map { emulator ->
                tasks.register<BuildEmulatorNativeLib>("buildHostNative${emulator.replaceFirstChar { it.uppercase() }}") {
                    group = "native"
                    dependsOn(shim)
                    nativeSources.from(nativeRoot.dir(emulator), nativeRoot.dir(NativeEmulators.COMMON_SUBDIR))
                    sourceDir.set(nativeRoot.dir(emulator))
                    cmakeExecutable.set(cmake)
                    workDir.set(layout.buildDirectory.dir("jvm-native/cxx/$emulator"))
                    outputDir.set(layout.buildDirectory.dir("jvm-native/out/$emulator"))
                    cacheKey.put("toolchain", "host")
                    cacheKey.put("buildType", "Release")
                    cacheKey.put("emulator", emulator)
                    variantConfigureArgs.put(
                        "host",
                        listOf(
                            "-DCMAKE_BUILD_TYPE=Release",
                            "-DCMAKE_C_FLAGS=$cFlags",
                            "-DCMAKE_CXX_FLAGS=$cFlags",
                            "-DCMAKE_SHARED_LINKER_FLAGS=-L${shimDirFile.absolutePath}",
                        ),
                    )
                }
            }

            tasks.register<Copy>("chipboxHostNativeLibs") {
                group = "native"
                description = "Host-builds every emulator native lib into build/jvm-native/libs/."
                perEmulator.forEach { from(it.flatMap { t -> t.outputDir.dir("host") }) }
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

    @org.gradle.api.tasks.TaskAction
    fun build() {
        val out = outputDir.get().asFile
        val inc = File(out, "include/android").apply { mkdirs() }
        File(inc, "log.h").writeText(LOG_H)
        val logC = File(out, "log.c").apply { writeText(LOG_C) }
        val logO = File(out, "log.o")
        exec.exec { commandLine("gcc", "-fPIC", "-c", logC.absolutePath, "-o", logO.absolutePath) }
        exec.exec { commandLine("ar", "rcs", File(out, "liblog.a").absolutePath, logO.absolutePath) }
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
