package net.sigmabeta.chipbox.uitest.harness

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * On-device artifact location for the instrumented (`androidDeviceTest`) run. There's no module
 * `build/` dir on the device, so:
 *
 *  1. Prefer AGP's `additionalTestOutputDir` — when set, files written there are pulled back to the
 *     host's `build/outputs/connected_android_test_additional_output/...` after the connected run.
 *  2. Otherwise fall back to the app's external files dir (`/sdcard/Android/data/<pkg>/files/...`), a
 *     known location that survives the run and can be `adb pull`-ed manually.
 *
 * Returning a dir here overrides the shared tmpdir default in `artifactDir()`. The desktop twin (in
 * `src/jvmTestPlatform`) returns null so the JVM run uses its build-dir/system-property default.
 */
internal fun platformArtifactDir(): File? {
    val additionalOutput = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
    if (additionalOutput != null) return File(additionalOutput)

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return context.getExternalFilesDir(null)?.let { File(it, "uitest-failures") }
}

/**
 * Read a test-run config value by [key]. On-device the build routes the `-P` flag in as an
 * instrumentation runner argument (a separate process from the host, so host system properties don't
 * reach it); fall back to a system property for completeness. The desktop twin reads system properties.
 */
internal fun platformTestArgument(key: String): String? =
    InstrumentationRegistry.getArguments().getString(key) ?: System.getProperty(key)
