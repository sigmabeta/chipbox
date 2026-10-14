package net.sigmabeta.chipbox.benchmark

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import java.time.OffsetDateTime
import kotlinx.serialization.encodeToString
import net.sigmabeta.chipbox.perfbench.Axis
import net.sigmabeta.chipbox.perfbench.CorpusRef
import net.sigmabeta.chipbox.perfbench.DeviceBenchmarkLayout
import net.sigmabeta.chipbox.perfbench.DeviceEnvironment
import net.sigmabeta.chipbox.perfbench.PerfConfig
import net.sigmabeta.chipbox.perfbench.PerfResult
import net.sigmabeta.chipbox.perfbench.PerfbenchJson
import net.sigmabeta.chipbox.perfbench.deviceFingerprint

/**
 * Device-side plumbing shared by the macrobenchmarks: the target package, the fix iteration count,
 * the device fingerprint facts, the build-stamped commits, the fixed corpus path, and the
 * `results.json` sink + shell.
 *
 * The macrobenchmark process instruments the app from outside and can't read its external files
 * dir on modern Android, so [corpusRoot] constructs that path for the record rather than touching
 * it — the app's `BenchmarkLibrarySeed` is what actually reads the pushed snapshot + corpus.
 */
internal object DeviceBenchmark {
    const val TARGET_PACKAGE = "net.sigmabeta.chipbox"

    /** Measured iterations per scenario, shared so every device axis reports a comparable repeat. */
    const val ITERATIONS = 5

    private const val RESULTS_FILE_NAME = "results.json"
    private const val CHIPBOX_COMMIT_ARG = "chipboxCommit"
    private const val SAGE_COMMIT_ARG = "sageCommit"
    private const val UNKNOWN = "unknown"

    /** The device facts a [DeviceEnvironment] records, probed from the running benchmark process. */
    fun environment(): DeviceEnvironment = DeviceEnvironment(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidBuild = Build.FINGERPRINT,
        abi = Build.SUPPORTED_ABIS.firstOrNull() ?: UNKNOWN,
        thermalStatus = thermalStatusName(InstrumentationRegistry.getInstrumentation().targetContext),
    )

    /** The two projected commits, passed as instrumentation arguments from the build (else unknown). */
    fun commits(): Pair<String, String> {
        val args = InstrumentationRegistry.getArguments()
        return (args.getString(CHIPBOX_COMMIT_ARG) ?: UNKNOWN) to (args.getString(SAGE_COMMIT_ARG) ?: UNKNOWN)
    }

    /**
     * Grants the target app All Files Access, so its benchmark seed can read the shared corpus.
     *
     * The macrobenchmark's compilation-mode reset uninstalls + reinstalls the app on API < 34 when
     * unrooted, which clears the app's appops — so this must run in each benchmark's `setupBlock`,
     * after that reset and before the measured launch. Idempotent, and harmless on devices where the
     * op is already allowed.
     */
    @Suppress("DEPRECATION")
    fun grantStorageAccess(device: UiDevice) {
        device.executeShellCommand("appops set $TARGET_PACKAGE MANAGE_EXTERNAL_STORAGE allow")
    }

    /**
     * Assembles one run's `results.json` shell — the fingerprint, commits and corpus block every
     * device axis shares — around the axis-specific [config] and [axes].
     */
    fun perfResult(
        environment: DeviceEnvironment,
        config: PerfConfig,
        axes: Map<String, Axis>,
    ): PerfResult {
        val commits = commits()
        return PerfResult(
            generated = OffsetDateTime.now().toString(),
            chipboxCommit = commits.first,
            sageCommit = commits.second,
            config = config,
            machine = deviceFingerprint(environment, commits.first, commits.second),
            // A device run reads no audio, so the manifest is empty; the root records the fixed
            // device corpus dir the pushed snapshot was rebased onto.
            corpus = CorpusRef(root = corpusRoot().absolutePath),
            axes = axes,
        )
    }

    /** Writes [result] as `results.json` into the run's additional test output dir. */
    fun write(result: PerfResult) {
        val outFile = File(resultsOutputDir(), RESULTS_FILE_NAME)
        outFile.parentFile?.mkdirs()
        outFile.writeText(PerfbenchJson.encodeToString(result))
    }

    /**
     * The fixed shared location the benchmark build seeds its library from (see
     * [DeviceBenchmarkLayout]), as an absolute path under shared external storage.
     */
    private fun corpusRoot(): File = File(
        externalStorageDir(),
        "${DeviceBenchmarkLayout.ROOT_DIR}/${DeviceBenchmarkLayout.CORPUS_DIR_NAME}",
    )

    @Suppress("DEPRECATION")
    private fun externalStorageDir(): File = android.os.Environment.getExternalStorageDirectory()

    /** The directory AGP pulls additional test output from, falling back to app files when unset. */
    private fun resultsOutputDir(): File {
        val additional = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        return if (additional != null) {
            File(additional)
        } else {
            File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "perfbench")
        }
    }

    /** The platform thermal-status label, or `"unknown"` on API levels that can't report it. */
    private fun thermalStatusName(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return UNKNOWN
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return UNKNOWN
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> UNKNOWN
        }
    }
}
