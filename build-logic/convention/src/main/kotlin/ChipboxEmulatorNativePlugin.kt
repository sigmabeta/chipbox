import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import net.sigmabeta.sage.plugins.components.namespaceFromPath
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Convention for the emulator `:native` modules. Instead of AGP's non-cacheable
 * `externalNativeBuild`, it builds the `.so`s with the shared cacheable [BuildEmulatorNativeLib]
 * task (NDK toolchain, one variant per ABI) and feeds them in as generated `jniLibs`, so the remote
 * build cache reuses native outputs when source is unchanged. The JVM host build uses the same task
 * via [ChipboxNativeHostPlugin].
 */
class ChipboxEmulatorNativePlugin : Plugin<Project> {

    private companion object {
        const val BUILD_TYPE = "RelWithDebInfo"
        val ABIS = listOf("arm64-v8a", "x86_64")
    }

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("sage.android")

            val emulatorDirName = projectDir.parentFile.name
            val derivedNamespace = namespaceFromPath()

            val skipNative = providers.gradleProperty("chipbox.skipNative").isPresent

            extensions.configure<LibraryExtension> {
                namespace = derivedNamespace
                // Pin ndkVersion ONLY when we actually emit jniLibs (AGP strips them with the NDK's
                // tools). Setting it unconditionally makes AGP resolve — and on CI install — the NDK
                // even for skip-native jobs (lint, static-analysis) that build nothing native.
                if (!skipNative) {
                    ndkVersion = NativeEmulators.NDK_VERSION
                }
            }

            // lint / static-analysis pass -Pchipbox.skipNative — nothing native to build here.
            if (skipNative) return@with

            // Use the highest SDK CMake (the cimg/android image ships one; locally it's whatever the
            // contributor installed) — it bundles a matching Ninja next to it. A PATH cmake like
            // /usr/bin/cmake has no sibling ninja, so the `-G Ninja` configure below would fail.
            val sdk = androidSdkDir()
            val cmake = resolveSdkCmake()
            val ninja = File(cmake.parentFile, "ninja")
            val toolchain = File(
                sdk,
                "ndk/${NativeEmulators.NDK_VERSION}/build/cmake/android.toolchain.cmake",
            )
            val nativeRoot = rootProject.layout.projectDirectory.dir("cbox/native")

            val buildNative = tasks.register<BuildEmulatorNativeLib>("buildEmulatorNativeLib") {
                nativeSources.from(
                    nativeRoot.dir(emulatorDirName),
                    nativeRoot.dir(NativeEmulators.COMMON_SUBDIR),
                )
                sourceDir.set(nativeRoot.dir(emulatorDirName))
                cmakeExecutable.set(cmake)
                workDir.set(layout.buildDirectory.dir("emulator-native/cxx"))
                outputDir.set(layout.buildDirectory.dir("emulator-native/jniLibs"))

                // Relocatable identity (the cache key) — no absolute paths here.
                cacheKey.put("toolchain", "ndk-${NativeEmulators.NDK_VERSION}")
                cacheKey.put("cmake", cmake.parentFile.parentFile.name)
                cacheKey.put("platform", NativeEmulators.ANDROID_PLATFORM)
                cacheKey.put("buildType", BUILD_TYPE)
                cacheKey.put("abis", ABIS.joinToString(","))

                // Per-ABI configure args (absolute toolchain/ninja paths kept out of the cache key).
                ABIS.forEach { abi ->
                    variantConfigureArgs.put(
                        abi,
                        listOf(
                            "-G", "Ninja",
                            "-DCMAKE_MAKE_PROGRAM=${ninja.absolutePath}",
                            "-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}",
                            "-DANDROID_ABI=$abi",
                            "-DANDROID_PLATFORM=${NativeEmulators.ANDROID_PLATFORM}",
                            "-DCMAKE_BUILD_TYPE=$BUILD_TYPE",
                        ),
                    )
                }
            }

            extensions.configure<LibraryAndroidComponentsExtension> {
                onVariants { variant ->
                    variant.sources.jniLibs?.addGeneratedSourceDirectory(
                        buildNative,
                        BuildEmulatorNativeLib::outputDir,
                    )
                }
            }
        }
    }
}
