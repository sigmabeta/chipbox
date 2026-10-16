package net.sigmabeta.chipbox.abrender

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device (arm64) half of the **bit-identity golden check**. It runs the exact same
 * [recordGoldenRun]/[verifyGoldenRun] engine the host JVM test does, so it can compare the device's
 * render against a golden reference recorded on the host (x86_64). Because PCM must be independent
 * of the CPU architecture, **any** difference here — target the same corpus, same seconds — is a
 * failure: this is the automated form of the cross-ISA `ab-compare.sh`.
 *
 * Self-instrumenting (no app under test): the corpus and the golden run are pushed into the test
 * process's external files dir and the candidate render is written back there. Drive it with
 * `golden-on-device.sh`, or by hand:
 *
 * ```
 * adb shell am instrument -w -r \
 *   -e class net.sigmabeta.chipbox.abrender.PcmGoldenDeviceTest#verifyGolden \
 *   -e dir    /sdcard/Android/data/net.sigmabeta.chipbox.abrender.test/files/abrender/in \
 *   -e golden /sdcard/Android/data/net.sigmabeta.chipbox.abrender.test/files/abrender/golden \
 *   net.sigmabeta.chipbox.abrender.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Every knob mirrors the host golden properties, passed as instrumentation args (`-e <key> <value>`):
 * `dir` (corpus), `golden` (the golden run dir), `work` (candidate output), `seconds`, `ext`, `game`,
 * `every-song`, `subsong`, `limit`, `max-wall-seconds`.
 */
@RunWith(AndroidJUnit4::class)
class PcmGoldenDeviceTest {
    @Test
    fun recordGolden() = runGolden(record = true)

    @Test
    fun verifyGolden() = runGolden(record = false)

    private fun runGolden(record: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bundle = InstrumentationRegistry.getArguments()
        val base = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)) {
            "No external files dir available on this device."
        }

        fun arg(vararg keys: String): String? = keys.firstNotNullOfOrNull { bundle.getString(it) }
        fun flag(vararg keys: String): Boolean = arg(*keys)?.equals("true", ignoreCase = true) == true

        val corpusDir = File(arg("dir") ?: File(base, "$DEFAULT_SUBDIR/in").path)
        val goldenDir = File(arg("golden", "golden.dir") ?: File(base, "$DEFAULT_SUBDIR/golden").path)
        val workDir = File(arg("work") ?: File(base, "$DEFAULT_SUBDIR/golden-work").path)
        corpusDir.mkdirs()
        goldenDir.mkdirs()
        workDir.mkdirs()

        println("abrender-golden: corpus = $corpusDir")
        println("abrender-golden: golden = $goldenDir")
        println("abrender-golden: work   = $workDir")

        // Fail loudly (with the path to fix) if the harness never pushed a corpus — otherwise the
        // run is a silent no-op that could look like a pass.
        val supported = EmulatorBank().supportedExtensions
        val playable = corpusDir.walkTopDown().count { it.isFile && it.extension.lowercase() in supported }
        assertTrue(
            "No playable files under $corpusDir — push a corpus there first, " +
                "e.g. `golden-on-device.sh --corpus <dir>`.",
            playable > 0,
        )

        val selection = GoldenSelection(
            seconds = arg("seconds")?.toIntOrNull() ?: GOLDEN_DEFAULT_SECONDS,
            extensions = arg("ext")
                ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet(),
            gameFilter = arg("game"),
            everySong = flag("every-song", "everySong"),
            subsong = arg("subsong")?.toIntOrNull() ?: 0,
            limit = arg("limit")?.toIntOrNull(),
            maxWallMillis = arg("max-wall-seconds", "maxWallSeconds")?.toLongOrNull()
                ?.times(MILLIS_PER_SECOND) ?: GOLDEN_DEFAULT_MAX_WALL_MILLIS,
        )

        if (record) {
            val summary = recordGoldenRun(corpusDir, goldenDir, selection)
            println("abrender-golden: recorded ${summary.rendered} track(s) → $goldenDir")
            assertTrue("recorded nothing — no supported tracks under $corpusDir?", summary.rendered > 0)
        } else {
            assertTrue(
                "no golden set at $goldenDir — push one (record on the host and copy it over) first",
                File(goldenDir, "metrics.tsv").exists(),
            )
            val report = verifyGoldenRun(corpusDir, goldenDir, workDir, selection)
            println("abrender-golden: ${report.describe()}")
            assertTrue(report.describe(), report.passed)
        }
    }

    private companion object {
        const val DEFAULT_SUBDIR = "abrender"
        const val MILLIS_PER_SECOND = 1000L
    }
}
