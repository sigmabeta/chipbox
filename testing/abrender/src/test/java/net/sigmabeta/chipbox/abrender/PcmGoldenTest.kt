package net.sigmabeta.chipbox.abrender

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Host-JVM half of the **bit-identity golden check**: it renders a caller-supplied corpus and either
 * records the reference PCM (`recordGolden`) or fails unless the fresh render is bit-identical to a
 * previously recorded set (`verifyGolden`). The engine lives in `:testing:abrender-core`
 * ([recordGoldenRun]/[verifyGoldenRun]); the on-device arm64 half is `PcmGoldenDeviceTest`, which
 * compares against the *same* reference — a difference there means the core is not architecture
 * independent, which is a failure by design.
 *
 * Driven entirely by system properties so the `recordPcmGolden`/`verifyPcmGolden` Gradle tasks can
 * point it at a corpus and golden dir. Run with no corpus (e.g. a bare `:testing:abrender:test`), both
 * tests skip rather than fail.
 */
class PcmGoldenTest {
    @Test
    fun recordGolden() {
        val env = GoldenEnv.fromSystemProperties()
        val corpus = env.corpus
        assumeTrue("no corpus (-Dchipbox.golden.corpus); golden record skipped", corpus != null)

        val goldenDir = File(env.goldensRoot, GOLDEN_LABEL)
        val summary = recordGoldenRun(checkNotNull(corpus), goldenDir, env.selection)
        assertTrue(
            "recorded nothing — no supported tracks under $corpus?",
            summary.rendered > 0,
        )
        println("Recorded ${summary.rendered} track(s) → $goldenDir")
    }

    @Test
    fun verifyGolden() {
        val env = GoldenEnv.fromSystemProperties()
        val corpus = env.corpus
        assumeTrue("no corpus (-Dchipbox.golden.corpus); golden verify skipped", corpus != null)

        val goldenDir = File(env.goldensRoot, GOLDEN_LABEL)
        assertTrue(
            "no golden set at $goldenDir — record it first: " +
                "./gradlew :testing:abrender:recordPcmGolden -Pchipbox.golden.corpus=<dir>",
            File(goldenDir, "metrics.tsv").exists(),
        )

        val workDir = File(env.workRoot, GOLDEN_LABEL)
        val report = verifyGoldenRun(checkNotNull(corpus), goldenDir, workDir, env.selection)
        println(report.describe())
        println("Candidate WAVs (listen here on divergence): $workDir/wav")
        assertTrue(report.describe(), report.passed)
    }
}

/** System-property configuration the golden Gradle tasks set (see `testing/abrender/build.gradle.kts`). */
private class GoldenEnv(
    val corpus: File?,
    val goldensRoot: File,
    val workRoot: File,
    val selection: GoldenSelection,
) {
    companion object {
        fun fromSystemProperties(): GoldenEnv {
            val extensions = System.getProperty("chipbox.golden.ext")
                ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet()
            return GoldenEnv(
                corpus = System.getProperty("chipbox.golden.corpus")
                    ?.takeIf { it.isNotBlank() }?.let(::File),
                goldensRoot = File(System.getProperty("chipbox.golden.dir") ?: "ab-goldens"),
                workRoot = File(System.getProperty("chipbox.golden.work") ?: "build/pcm-golden-work"),
                selection = GoldenSelection(
                    seconds = System.getProperty("chipbox.golden.seconds")?.toIntOrNull()
                        ?: GOLDEN_DEFAULT_SECONDS,
                    extensions = extensions,
                    gameFilter = System.getProperty("chipbox.golden.game"),
                    everySong = System.getProperty("chipbox.golden.every-song")?.toBoolean() == true,
                    limit = System.getProperty("chipbox.golden.limit")?.toIntOrNull(),
                ),
            )
        }
    }
}
