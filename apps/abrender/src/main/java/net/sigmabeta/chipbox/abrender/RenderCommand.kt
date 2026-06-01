package net.sigmabeta.chipbox.abrender

import java.io.File

private const val MAX_SLUG_LENGTH = 48
private const val HASH_SUFFIX_LENGTH = 8
private const val MILLIS_PER_SECOND = 1000.0

/** Sanitizes a string into a filesystem-safe fragment for a WAV filename. */
private fun slug(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(MAX_SLUG_LENGTH).ifEmpty { "x" }

/** A short stable suffix so two tracks that slug to the same name can't collide. */
@Suppress("MagicNumber") // FNV-1a offset basis + prime
private fun shortHash(key: String): String {
    var h = -0x340d631b7bdddcdbL
    for (c in key) h = (h xor c.code.toLong()) * 0x100000001b3L
    return java.lang.Long.toHexString(h).takeLast(HASH_SUFFIX_LENGTH)
}

/**
 * `render` — walk the corpus directory, select tracks, and render each to a labelled run directory
 * (`<out>/<label>/`) as a WAV plus a row in `metrics.tsv`. Designed to run unattended over 400+
 * games: every track is rendered in isolation and a per-track failure is recorded, never fatal.
 */
fun runRender(args: Args) {
    val runDir = File(args.outDir, args.label).apply { mkdirs() }
    val wavDir = File(runDir, "wav").apply { mkdirs() }
    val metricsFile = File(runDir, "metrics.tsv")

    val bank = EmulatorBank()
    val selected = walkCorpus(
        root = args.corpusDir,
        supported = bank.supportedExtensions,
        extensionFilter = args.extensions,
        gameFilter = args.gameFilter,
        everySong = args.everySong,
        subsong = args.subsong,
    ).let { if (args.limit != null) it.take(args.limit) else it }

    if (selected.isEmpty()) {
        println(
            "No matching tracks under ${args.corpusDir} " +
                "(ext=${args.extensions ?: "any supported"}, game~=${args.gameFilter ?: "any"}).",
        )
        return
    }

    val done = if (args.overwrite) emptyMap() else readMetrics(metricsFile)
    val writeHeader = !metricsFile.exists() || args.overwrite
    val sink = MetricsSink(metricsFile, append = !writeHeader)

    println("Rendering ${selected.size} track(s) → $runDir  (${args.seconds}s each)")
    if (done.isNotEmpty()) println("Resuming: ${done.size} already present, will skip.")

    var rendered = 0
    var skipped = 0
    var failed = 0
    selected.forEachIndexed { index, track ->
        val position = "[${index + 1}/${selected.size}]"
        val existing = done[track.trackKey]
        if (existing != null && File(wavDir, existing.wav).exists()) {
            skipped++
            return@forEachIndexed
        }

        val wavName = "${slug(track.game)}__${slug(track.title)}__t${track.trackNumber}__" +
            "${shortHash(track.trackKey)}.wav"
        val started = System.currentTimeMillis()
        val metrics = bank.render(track, args.seconds, File(wavDir, wavName), args.maxWallMillis)
        sink.write(metrics)

        val elapsed = (System.currentTimeMillis() - started) / MILLIS_PER_SECOND
        if (metrics.error.isEmpty()) {
            rendered++
            println(
                "$position ${track.game} — ${track.title}  " +
                "rms=${dbStr(metrics.rmsDbfs)} peak=${dbStr(metrics.peakDbfs)}  (${elapsed.fixed(decimals = 1)}s)"
            )
        } else {
            failed++
            println("$position ${track.game} — ${track.title}  ERROR: ${metrics.error}")
        }
    }

    sink.close()
    println("Done. rendered=$rendered skipped=$skipped failed=$failed → $metricsFile")
}

private fun dbStr(v: Double): String = if (v == Double.NEGATIVE_INFINITY) "-inf" else v.fixed(decimals = 1)

/** Appends metrics rows as they're produced, so a crashed run keeps its progress for `--resume`. */
private class MetricsSink(file: File, append: Boolean) {
    // FileOutputStream(append) is explicit: File.bufferedWriter() would truncate, losing prior rows.
    private val writer = java.io.FileOutputStream(file, append).bufferedWriter(Charsets.UTF_8).also {
        if (!append) {
            it.write(TrackMetrics.TSV_HEADER)
            it.newLine()
        }
    }

    fun write(metrics: TrackMetrics) {
        writer.write(metrics.toTsvRow())
        writer.newLine()
        writer.flush()
    }

    fun close() = writer.close()
}
