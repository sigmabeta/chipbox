import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.plugins.ExtensionAware

/*
 * Shared render core for the A/B harness, factored out of `:apps:abrender` so it can run on BOTH the
 * JVM (the desktop CLI in `:apps:abrender`, which only proves the x86_64 emulators) and a real Android
 * device (the `androidDeviceTest` below, which proves the arm64 ones). The render loop, metrics, WAV
 * writer, and corpus walker live in `jvmSharedMain` (`src/main/java`) — pure `java.*`, no Android — so
 * both targets render byte-identically and the desktop `abrender diff` can compare an on-device run
 * against an x86_64 oracle with no platform-specific analysis code.
 *
 * It must be KMP (not `sage.jvm`): only then does an Android consumer resolve the *android* variant of
 * each emulator `:real`, which pulls the `:native` companion's arm64 `.so` into the device-test APK.
 */
plugins {
    alias(libs.plugins.sage.kmp)
}

kotlin {
    (this as ExtensionAware).extensions.configure(
        KotlinMultiplatformAndroidLibraryExtension::class.java,
    ) {
        namespace = "net.sigmabeta.chipbox.abrender"

        packaging {
            jniLibs {
                // A transitive dep (libandroidx.graphics.path.so) ships ABIs we build no emulator
                // cores for (armeabi-v7a, armeabi, x86). Their mere presence can make the package
                // manager pick a core-less primary ABI and extract a lib dir with none of our cores,
                // so `System.loadLibrary("vgmstream")` fails with "not found" even though the real .so
                // is in the APK. Drop those stray ABIs so the install resolves one we actually build
                // for (arm64-v8a on device, x86_64 on emulator).
                excludes += setOf("**/armeabi-v7a/**", "**/armeabi/**", "**/x86/**")

                // Extract the .so to the on-disk native lib dir at install time (extractNativeLibs=
                // true) rather than mmap'ing them from the APK, sidestepping any zip page-alignment
                // requirement on-device. This is a dev/test harness, so the legacy-packaging warning
                // is fine.
                useLegacyPackaging = true
            }
        }

        // The on-device render harness. There is no app under test — the instrumentation self-renders
        // a corpus pushed to its external files dir and writes WAVs + metrics.tsv back there for adb
        // to pull. See run-on-device.sh.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            applicationId = "net.sigmabeta.chipbox.abrender.test"
        }
    }

    sourceSets {
        // Emulator base type + every backend's JNI wrapper, plus EbuR128/SHORTS_PER_FRAME for the
        // loudness metric — same set `:apps:abrender` used to depend on directly. For the android
        // target these resolve to variants whose `androidMain` carries a runtimeOnly dep on the
        // matching `:native` module, so the arm64 `.so` lands in the device-test APK.
        getByName("jvmSharedMain").dependencies {
            implementation(projects.cbox.common.player.emulators.api)
            implementation(projects.cbox.common.player.emulators.gba.real)
            implementation(projects.cbox.common.player.emulators.gme.real)
            implementation(projects.cbox.common.player.emulators.ncsf.real)
            implementation(projects.cbox.common.player.emulators.psf.real)
            implementation(projects.cbox.common.player.emulators.ssf.real)
            implementation(projects.cbox.common.player.emulators.twosf.real)
            implementation(projects.cbox.common.player.emulators.usf.real)
            implementation(projects.cbox.common.player.emulators.vgm.real)
            implementation(projects.cbox.common.player.emulators.vgmstream.real)
            implementation(projects.cbox.common.player.common.api)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(libs.junit4)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.test.rules)
        }
    }
}
