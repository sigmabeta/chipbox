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
import net.sigmabeta.chipbox.perfbench.DeviceBenchmarkLayout
import net.sigmabeta.chipbox.perfbench.DeviceEnvironment
import net.sigmabeta.chipbox.perfbench.PerfConfig
import net.sigmabeta.chipbox.perfbench.PerfResult
import net.sigmabeta.chipbox.perfbench.PerfbenchJson
import net.sigmabeta.chipbox.perfbench.axisResultFileName
import net.sigmabeta.chipbox.perfbench.corpusPin
import net.sigmabeta.chipbox.perfbench.deviceFingerprint

/**
 * Device-side plumbing shared by the macrobenchmarks: the target package, the fix iteration count,
 * the device fingerprint facts, the build-stamped commits, the fixed corpus path, and the per-axis
 * result sink + shell.
 *
 * The macrobenchmark process runs in its own APK and can't read the app's external files dir on
 * modern Android; it reads the pushed corpus + snapshot from shared external storage instead, under
 * All Files Access it grants itself in [grantStorageAccess]. That read is what pins each result
 * ([corpusPin]) — the app's `BenchmarkLibrarySeed` separately seeds the library from the snapshot.
 */
internal object DeviceBenchmark {
    const val TARGET_PACKAGE = "net.sigmabeta.chipbox"

    /** Measured iterations per scenario, shared so every device axis reports a comparable repeat. */
    const val ITERATIONS = 5

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
     * Grants All Files Access to both the target app — so its benchmark seed can read the pushed
     * corpus + snapshot — and the benchmark process itself, so it can hash that same corpus +
     * snapshot to pin every result ([corpusPin]). The benchmark APK declares
     * `MANAGE_EXTERNAL_STORAGE`; the op defaults to denied, so it is allowed here.
     *
     * The macrobenchmark's compilation-mode reset uninstalls + reinstalls the app on API < 34 when
     * unrooted, which clears the app's appops — so this must run in each benchmark's `setupBlock`,
     * after that reset and before the measured launch. Idempotent, and harmless on devices where the
     * op is already allowed.
     */
    @Suppress("DEPRECATION")
    fun grantStorageAccess(device: UiDevice) {
        val benchmarkPackage = InstrumentationRegistry.getInstrumentation().context.packageName
        device.executeShellCommand("appops set $TARGET_PACKAGE MANAGE_EXTERNAL_STORAGE allow")
        device.executeShellCommand("appops set $benchmarkPackage MANAGE_EXTERNAL_STORAGE allow")
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
            config = config,
            machine = deviceFingerprint(environment, commits.first, commits.second),
            corpus = corpusPin(corpusRoot(), snapshotFile()),
            axes = axes,
        )
    }

    /**
     * Writes [result] into the run's additional test output dir, one artifact per axis it carries.
     *
     * Every `@Test` in a run writes into the *same* directory, so each axis goes to its own
     * [axisResultFileName] rather than a shared `results.json` (see [axisResultFileName] for why).
     * The run shell — device fingerprint + corpus — is carried on every artifact, so each stands
     * on its own.
     */
    fun write(result: PerfResult) {
        val outDir = resultsOutputDir().apply { mkdirs() }
        result.axes.forEach { (axis, axisResult) ->
            val perAxis = result.copy(axes = mapOf(axis to axisResult))
            File(outDir, axisResultFileName(axis)).writeText(PerfbenchJson.encodeToString(perAxis))
        }
    }

    /**
     * The fixed shared location the benchmark build seeds its library from (see
     * [DeviceBenchmarkLayout]), as an absolute path under shared external storage. The audio axis
     * renders the same pushed corpus the UI/startup axes' results are pinned to.
     */
    fun corpusRoot(): File = File(
        externalStorageDir(),
        "${DeviceBenchmarkLayout.ROOT_DIR}/${DeviceBenchmarkLayout.CORPUS_DIR_NAME}",
    )

    /** The pushed library snapshot the app seeds from, sibling to [corpusRoot] under the same root. */
    internal fun snapshotFile(): File = File(
        externalStorageDir(),
        "${DeviceBenchmarkLayout.ROOT_DIR}/${DeviceBenchmarkLayout.SNAPSHOT_FILE_NAME}",
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
        val powerManager = context.getSystemService(PowerManager::class.java)
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || powerManager == null) {
            UNKNOWN
        } else {
            when (powerManager.currentThermalStatus) {
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
}
