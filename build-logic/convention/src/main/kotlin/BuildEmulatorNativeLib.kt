import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Builds an emulator's native `.so`(s) by invoking CMake directly, and emits them into a per-variant
 * output tree — a **cacheable** replacement for AGP's `externalNativeBuild` (not cacheable) and for
 * apps/jvm's `Exec` build tasks (incremental but not cacheable). One task, both worlds:
 *  - **Android:** one variant per ABI (arm64-v8a, x86_64), configured with the NDK toolchain.
 *  - **JVM host:** a single "host" variant, configured with the host toolchain + log shim.
 *
 * It's `@CacheableTask` and fingerprints the native sources by RELATIVE path, so the remote build
 * cache reuses the `.so`s when source is unchanged (~97% of commits). The cache key is
 * [nativeSources] + [cacheKey]; everything machine-specific (cmake path, absolute toolchain/JDK/shim
 * paths inside [variantConfigureArgs]) is `@Internal` so the key stays relocatable across machines.
 * The caller is responsible for putting every relocatable determinant of the output into [cacheKey].
 */
@CacheableTask
abstract class BuildEmulatorNativeLib : DefaultTask() {

    /** The emulator's CMake tree + any shared sources it pulls in, fingerprinted by content. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeSources: ConfigurableFileCollection

    /** Relocatable determinants of the output (toolchain id+version, ABI list, build type, …). */
    @get:Input abstract val cacheKey: MapProperty<String, String>

    /** variant name (e.g. "arm64-v8a" or "host") → extra CMake configure args, which MAY contain
     *  machine-specific absolute paths — hence `@Internal`, with [cacheKey] carrying the identity. */
    @get:Internal abstract val variantConfigureArgs: MapProperty<String, List<String>>

    @get:Internal abstract val cmakeExecutable: RegularFileProperty
    @get:Internal abstract val sourceDir: DirectoryProperty
    @get:Internal abstract val workDir: DirectoryProperty

    /** Cap on parallel compile jobs for `cmake --build`. Unset → `-j` (the native tool's default; for
     *  Make that's *unbounded*). Set it to bound memory on a cold build of many files (e.g. CI, where a
     *  full mGBA compile under `make -j` can OOM the container). `@Internal`: speed/memory only, not the
     *  output — must not affect the cache key. */
    @get:Internal abstract val maxParallelJobs: Property<Int>

    /** `<variant>/lib*.so`. */
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun build() {
        val cmake = cmakeExecutable.get().asFile.absolutePath
        val src = sourceDir.get().asFile.absolutePath
        val out = outputDir.get().asFile
        out.deleteRecursively()

        variantConfigureArgs.get().forEach { (variant, extraArgs) ->
            val cxx = workDir.get().asFile.resolve(variant)
            val variantOut = out.resolve(variant).apply { mkdirs() }
            exec.exec {
                commandLine(
                    buildList {
                        add(cmake)
                        add("-S"); add(src)
                        add("-B"); add(cxx.absolutePath)
                        add("-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${variantOut.absolutePath}")
                        // A Windows `.dll` is a RUNTIME artifact (only the `.dll.a` import lib is a
                        // LIBRARY one), so the cross-build needs this set too or the `.dll` lands in the
                        // build tree. Harmless for the Android/Linux `.so`, which are LIBRARY artifacts.
                        add("-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${variantOut.absolutePath}")
                        addAll(extraArgs)
                    },
                )
            }
            val parallelArg = maxParallelJobs.orNull?.let { listOf("--parallel", it.toString()) } ?: listOf("-j")
            exec.exec {
                commandLine(buildList { add(cmake); add("--build"); add(cxx.absolutePath); addAll(parallelArg) })
            }
        }
    }
}
