package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.lang.management.ManagementFactory
import java.time.OffsetDateTime
import kotlin.math.roundToInt
import net.sigmabeta.chipbox.abrender.EmulatorBank
import net.sigmabeta.chipbox.abrender.SelectedTrack
import net.sigmabeta.chipbox.abrender.walkCorpus

private const val MILLIS_PER_SECOND = 1000L
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val DEFAULT_MAX_WALL_SECONDS = 120L
private const val DECIMAL_BASE = 10
private const val UNKNOWN = "unknown"

/**
 * The host audio axis. Thin orchestration over the reused A/B render engine: for each scenario it
 * picks one representative track from the corpus, warms up once, then times N renders and reduces
 * them to one RTF. The write-and-measure loop is exactly [EmulatorBank.render] — no new render seam.
 */
fun runAudio(args: AudioArgs): PerfResult {
    args.outDir.mkdirs()
    val wavDir = File(args.outDir, "wav").apply { mkdirs() }
    val bank = EmulatorBank()
    val scenarios = args.only ?: AudioScenario.all
    val results = mutableListOf<AudioResult>()
    val measuredFiles = mutableListOf<File>()

    for (scenario in scenarios) {
        val track = representativeTrack(bank, args.corpusDir, scenario)
        if (track == null) {
            println("${scenario.id}: no ${scenario.extensions.joinToString("/")} tracks under ${args.corpusDir}")
            continue
        }
        benchmarkScenario(bank, track, scenario, args, wavDir)?.let { result ->
            results += result
            measuredFiles += File(track.path)
        }
    }

    val repoRoot = resolveRepoRoot()
    val (chipboxCommit, sageCommit) = if (repoRoot != null) projectCommits(repoRoot) else UNKNOWN to UNKNOWN
    val result = PerfResult(
        generated = OffsetDateTime.now().toString(),
        config = PerfConfig(scenarios = scenarios.map { it.id }),
        machine = hostFingerprint(SystemHostEnvironment.detect(), chipboxCommit, sageCommit),
        corpus = CorpusRef(
            root = args.corpusDir.absolutePath,
            manifest = corpusManifest(args.corpusDir, measuredFiles),
        ),
        axes = mapOf(
            "audio" to AudioAxis(
                config = AudioAxisConfig(sampleSeconds = args.sampleSeconds, repeats = args.repeat),
                results = results,
            ),
        ),
    )
    val outFile = File(args.outDir, RESULTS_FILE_NAME)
    outFile.writeText(PerfbenchJson.encodeToString(result))
    println("Wrote $outFile")
    return result
}

/** Benchmarks one scenario's already-selected track, printing a one-line verdict; `null` on failure. */
private fun benchmarkScenario(
    bank: EmulatorBank,
    track: SelectedTrack,
    scenario: AudioScenario,
    args: AudioArgs,
    wavDir: File,
): AudioResult? {
    val measurement = measureScenario(bank, track, scenario, args, File(wavDir, "${scenario.id}.wav"))
    return measurement?.let { audioResult(it) }?.also { result ->
        println(
            "${scenario.id}: ${track.game} — ${track.title}  " +
                "RTF ${result.rtf.round(2)}  (${result.median.round(2)}s median for ${args.sampleSeconds}s audio)",
        )
    }
}

/** One track per scenario, chosen by the same deterministic walker the A/B harness uses. */
private fun representativeTrack(bank: EmulatorBank, corpusDir: File, scenario: AudioScenario): SelectedTrack? =
    walkCorpus(
        root = corpusDir,
        supported = bank.supportedExtensions,
        extensionFilter = scenario.extensions,
        gameFilter = null,
        everySong = false,
        subsong = 0,
    ).firstOrNull()

/** Warm-up once (untimed), then [AudioArgs.repeat] timed renders; `null` if no repeat succeeded. */
private fun measureScenario(
    bank: EmulatorBank,
    track: SelectedTrack,
    scenario: AudioScenario,
    args: AudioArgs,
    wav: File,
): AudioMeasurement? {
    val maxWallMillis = DEFAULT_MAX_WALL_SECONDS * MILLIS_PER_SECOND
    val warmUp = bank.render(track, args.sampleSeconds, wav, maxWallMillis)
    if (warmUp.error.isNotEmpty()) {
        println("${scenario.id}: ${track.game} — ${track.title}  ERROR: ${warmUp.error}")
        return null
    }

    val wallSeconds = mutableListOf<Double>()
    val cpuSeconds = mutableListOf<Double>()
    var frames = warmUp.frames
    var sampleRate = warmUp.sampleRate
    repeat(args.repeat) {
        val wallStart = System.nanoTime()
        val cpuStart = threadCpuNanos()
        val metrics = bank.render(track, args.sampleSeconds, wav, maxWallMillis)
        val wall = (System.nanoTime() - wallStart) / NANOS_PER_SECOND
        val cpuEnd = threadCpuNanos()
        if (metrics.error.isNotEmpty()) {
            println("${scenario.id}: repeat failed: ${metrics.error}")
            return@repeat
        }
        frames = metrics.frames
        sampleRate = metrics.sampleRate
        wallSeconds += wall
        if (cpuStart >= 0 && cpuEnd >= 0) cpuSeconds += (cpuEnd - cpuStart) / NANOS_PER_SECOND
    }

    return wallSeconds.takeIf { it.isNotEmpty() }?.let {
        AudioMeasurement(scenario.id, frames, sampleRate, it, cpuSeconds)
    }
}

/** Thread CPU time, or -1 when the JVM can't report it (the caller then falls back to wall time). */
private fun threadCpuNanos(): Long {
    val bean = ManagementFactory.getThreadMXBean()
    return if (bean.isCurrentThreadCpuTimeSupported) bean.currentThreadCpuTime else -1L
}

/** Rounds to [decimals] places for console output only; the JSON keeps full precision. */
private fun Double.round(decimals: Int): Double {
    var factor = 1.0
    repeat(decimals) { factor *= DECIMAL_BASE }
    return (this * factor).roundToInt() / factor
}
