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
private const val SCRATCH_RADIX = 16
private const val UNKNOWN = "unknown"

/**
 * The host audio axis. Thin orchestration over the reused A/B render engine: it walks every
 * supported file in the corpus (optionally narrowed by [AudioArgs.filter]), warms up once, then
 * times N renders and reduces them to one RTF keyed by the file's corpus-relative path. A file that
 * fails to render is recorded as an errored result rather than dropped, so a run never silently
 * shrinks. The write-and-measure loop is exactly [EmulatorBank.render] — no new render seam.
 */
fun runAudio(args: AudioArgs): PerfResult {
    args.outDir.mkdirs()
    val wavDir = File(args.outDir, "wav").apply { mkdirs() }
    val bank = EmulatorBank()
    val root = args.corpusDir.absoluteFile
    val matchesFilter = corpusPathFilter(args.filter)
    val tracks = walkCorpus(
        root = root,
        supported = bank.supportedExtensions,
        extensionFilter = null,
        gameFilter = null,
        everySong = true,
        subsong = 0,
    ).filter { matchesFilter(it.corpusRelativePath(root)) }

    if (tracks.isEmpty()) {
        println("No renderable tracks under $root${args.filter?.let { " matching '$it'" } ?: ""}")
    }

    val results = mutableListOf<AudioResult>()
    val measuredFiles = mutableListOf<File>()
    for (track in tracks) {
        val path = track.corpusRelativePath(root)
        val wav = File(wavDir, "${scratchWavName(path)}.wav")
        results += measureTrack(bank, track, path, args, wav)
        measuredFiles += File(track.path)
    }

    val repoRoot = resolveRepoRoot()
    val (chipboxCommit, sageCommit) = if (repoRoot != null) projectCommits(repoRoot) else UNKNOWN to UNKNOWN
    val result = PerfResult(
        generated = OffsetDateTime.now().toString(),
        config = PerfConfig(scenarios = results.map { it.path }),
        machine = hostFingerprint(SystemHostEnvironment.detect(), chipboxCommit, sageCommit),
        corpus = CorpusRef(
            root = root.absolutePath,
            manifest = corpusManifest(root, measuredFiles),
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

/** The file's path relative to the corpus root, always `/`-separated (the manifest's own key). */
private fun SelectedTrack.corpusRelativePath(root: File): String =
    File(path).relativeToOrSelf(root).invariantSeparatorsPath

/** A stable, filesystem-safe scratch WAV name derived from a corpus path. */
private fun scratchWavName(path: String): String =
    "${path.hashCode().toUInt().toString(SCRATCH_RADIX)}.wav"

/**
 * Warms up once (untimed), then times [AudioArgs.repeat] renders, reducing them to one result. A
 * warm-up failure, or all repeats failing, becomes an errored result carrying the failure — never
 * an omission.
 */
private fun measureTrack(
    bank: EmulatorBank,
    track: SelectedTrack,
    path: String,
    args: AudioArgs,
    wav: File,
): AudioResult {
    val maxWallMillis = DEFAULT_MAX_WALL_SECONDS * MILLIS_PER_SECOND
    val warmUp = bank.render(track, args.sampleSeconds, wav, maxWallMillis)
    if (warmUp.error.isNotEmpty()) return errored(track, path, warmUp.error)

    val wallSeconds = mutableListOf<Double>()
    val cpuSeconds = mutableListOf<Double>()
    var frames = warmUp.frames
    var sampleRate = warmUp.sampleRate
    var repeatError: String? = null
    repeat(args.repeat) {
        val wallStart = System.nanoTime()
        val cpuStart = threadCpuNanos()
        val metrics = bank.render(track, args.sampleSeconds, wav, maxWallMillis)
        val wall = (System.nanoTime() - wallStart) / NANOS_PER_SECOND
        val cpuEnd = threadCpuNanos()
        if (metrics.error.isNotEmpty()) {
            repeatError = metrics.error
            return@repeat
        }
        frames = metrics.frames
        sampleRate = metrics.sampleRate
        wallSeconds += wall
        if (cpuStart >= 0 && cpuEnd >= 0) cpuSeconds += (cpuEnd - cpuStart) / NANOS_PER_SECOND
    }

    return if (wallSeconds.isEmpty()) {
        errored(track, path, repeatError ?: "no timed repeat produced audio")
    } else {
        audioResult(AudioMeasurement(path, frames, sampleRate, wallSeconds, cpuSeconds)).also { result ->
            println(
                "$path: ${track.game} — ${track.title}  RTF ${result.rtf?.round(2)}  " +
                    "(${result.median?.round(2)}s median for ${args.sampleSeconds}s audio)",
            )
        }
    }
}

/** Records a failed render as an errored result (and says why), instead of silently dropping it. */
private fun errored(track: SelectedTrack, path: String, error: String): AudioResult {
    println("$path: ${track.game} — ${track.title}  ERROR: $error")
    return erroredAudioResult(path, error)
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
