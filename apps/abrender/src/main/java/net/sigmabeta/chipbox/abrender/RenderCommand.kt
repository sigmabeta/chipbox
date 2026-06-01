package net.sigmabeta.chipbox.abrender

import java.io.File

private const val MAX_SLUG_LENGTH = 48
private const val HASH_SUFFIX_LENGTH = 8
private const val MILLIS_PER_SECOND = 1000.0

// A track that hangs inside a single uninterruptible native call (loadTrack / generateBuffer) can't
// be unblocked from the JVM — the per-buffer wall cap in Renderer never gets to fire. A daemon
// watchdog detects this, records the track as WEDGED so a resume skips it, and halts the shard.
private const val WATCHDOG_POLL_MILLIS = 2000L
private const val WATCHDOG_GRACE_MILLIS = 30_000L
private const val WATCHDOG_EXIT_CODE = 7

/** Published by the render loop before each track so the watchdog knows what (and since when). */
private class RenderWatch {
    @Volatile var track: SelectedTrack? = null

    @Volatile var startMillis: Long = 0L
}

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
    val walked = walkCorpus(
        root = args.corpusDir,
        supported = bank.supportedExtensions,
        extensionFilter = args.extensions,
        gameFilter = args.gameFilter,
        everySong = args.everySong,
        subsong = args.subsong,
    )
    // Partition by global (sorted) index so n shards are disjoint and together cover everything; the
    // ordering is deterministic, so every shard process computes the same split. Limit caps per shard.
    val sharded =
        if (args.shards > 1) walked.filterIndexed { i, _ -> i % args.shards == args.shard } else walked
    val selected = if (args.limit != null) sharded.take(args.limit) else sharded

    if (selected.isEmpty()) {
        println(
            "No matching tracks under ${args.corpusDir} " +
                "(ext=${args.extensions ?: "any supported"}, game~=${args.gameFilter ?: "any"}).",
        )
        return
    }

    val baseDone = if (args.overwrite) emptyMap() else readMetrics(metricsFile)
    val writeHeader = !metricsFile.exists() || args.overwrite
    val sink = MetricsSink(metricsFile, append = !writeHeader)

    val shardTag = if (args.shards > 1) "  (shard ${args.shard}/${args.shards})" else ""
    println("Rendering ${selected.size} track(s) → $runDir  (${args.seconds}s each)$shardTag")
    if (baseDone.isNotEmpty()) println("Resuming: ${baseDone.size} already present, will skip.")

    // A marker left by a crash/hang on the previous launch becomes a recorded failure, so we skip it.
    val inflight = File(runDir, ".inflight")
    val done = recoverInflight(inflight, baseDone, sink)?.let { baseDone + (it.trackKey to it) } ?: baseDone

    val watch = RenderWatch()
    startWatchdog(watch, args.maxWallMillis)

    val tally = renderEach(selected, done, wavDir, bank, sink, watch, inflight, args)

    inflight.delete() // clean finish leaves no marker
    sink.close()
    println("Done. rendered=${tally.rendered} skipped=${tally.skipped} failed=${tally.failed} → $metricsFile")
}

private class Tally(var rendered: Int = 0, var skipped: Int = 0, var failed: Int = 0)

@Suppress("LongParameterList") // each arg is a distinct render input; bundling them would just hide that
private fun renderEach(
    selected: List<SelectedTrack>,
    done: Map<String, TrackMetrics>,
    wavDir: File,
    bank: EmulatorBank,
    sink: MetricsSink,
    watch: RenderWatch,
    inflight: File,
    args: Args,
): Tally {
    val tally = Tally()
    selected.forEachIndexed { index, track ->
        val position = "[${index + 1}/${selected.size}]"
        val existing = done[track.trackKey]
        // Skip tracks already rendered (wav on disk) AND ones with a recorded error (a bad/wedged
        // track is deterministic — re-rendering would just fail or hang again). --overwrite forces both.
        if (existing != null && (File(wavDir, existing.wav).exists() || existing.error.isNotEmpty())) {
            tally.skipped++
            return@forEachIndexed
        }

        val wavName = "${slug(track.game)}__${slug(track.title)}__t${track.trackNumber}__" +
            "${shortHash(track.trackKey)}.wav"
        val started = System.currentTimeMillis()
        // Write-ahead marker first: if this render crashes (SIGSEGV) or hangs, the next launch skips it.
        writeInflight(inflight, track, wavName)
        // Publish to the watchdog (start first, then track last so it never reads a stale start).
        watch.startMillis = started
        watch.track = track
        val metrics = bank.render(track, args.seconds, File(wavDir, wavName), args.maxWallMillis)
        watch.track = null
        sink.write(metrics)

        val elapsed = (System.currentTimeMillis() - started) / MILLIS_PER_SECOND
        if (metrics.error.isEmpty()) {
            tally.rendered++
            println(
                "$position ${track.game} — ${track.title}  " +
                    "rms=${dbStr(metrics.rmsDbfs)} peak=${dbStr(metrics.peakDbfs)}  " +
                    "(${elapsed.fixed(decimals = 1)}s)",
            )
        } else {
            tally.failed++
            println("$position ${track.game} — ${track.title}  ERROR: ${metrics.error}")
        }
    }
    return tally
}

/**
 * Starts a daemon thread that halts the shard if the currently-rendering track exceeds the wall cap
 * plus a grace margin — the signature of a hang inside one uninterruptible native call. It doesn't
 * write anything (the main thread owns the sink); the in-flight marker on disk lets the next launch
 * record the track as failed and skip it. Grace is added on top of [maxWallMillis] because a merely
 * slow-but-progressing track is already bounded by Renderer's per-buffer deadline; only a true
 * single-call hang outlives that.
 */
private fun startWatchdog(watch: RenderWatch, maxWallMillis: Long) {
    val deadlineMillis = maxWallMillis + WATCHDOG_GRACE_MILLIS
    val watchdog = Thread {
        // A thrown InterruptedException (JVM shutting down) just ends the daemon quietly.
        runCatching {
            while (true) {
                Thread.sleep(WATCHDOG_POLL_MILLIS)
                val track = watch.track
                if (track != null && System.currentTimeMillis() - watch.startMillis > deadlineMillis) {
                    System.err.println(
                        "WATCHDOG: '${track.trackKey}' wedged > ${deadlineMillis}ms in an " +
                            "uninterruptible native call; halting shard (recovered on relaunch).",
                    )
                    Runtime.getRuntime().halt(WATCHDOG_EXIT_CODE)
                }
            }
        }
    }
    watchdog.isDaemon = true
    watchdog.name = "abrender-watchdog"
    watchdog.start()
}

/**
 * Write-ahead marker naming the track about to render. If the process then dies — a native crash
 * (SIGSEGV) or a watchdog halt on a hang — the marker survives, and [recoverInflight] turns it into a
 * recorded failure on the next launch so the resume skips past it instead of looping on it forever.
 */
private fun writeInflight(file: File, track: SelectedTrack, wavName: String) {
    file.writeText(listOf(track.trackKey, track.game, track.title, track.extension, wavName).joinToString("\t"))
}

/**
 * If a marker from a previous launch names a track with no successful row yet, record it as a failure
 * (it crashed or hung in native code) so it's skipped, and return that row. Clears the marker.
 */
@Suppress("ReturnCount", "MagicNumber") // guard clauses for each non-recoverable case; f[n] = marker columns
private fun recoverInflight(file: File, done: Map<String, TrackMetrics>, sink: MetricsSink): TrackMetrics? {
    if (!file.exists()) return null
    val f = file.readText().split("\t")
    file.delete()
    val trackKey = f.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
    if (done.containsKey(trackKey)) return null // it actually completed; marker is just stale
    val row = TrackMetrics(
        trackKey = trackKey, game = f.getOrElse(1) { "" }, title = f.getOrElse(2) { "" },
        extension = f.getOrElse(3) { "" }, sampleRate = 0, frames = 0L,
        rmsDbfs = Double.NEGATIVE_INFINITY, peakDbfs = Double.NEGATIVE_INFINITY, lufs = Double.NaN,
        pcmHash = 0L, error = "native call did not return (crash or hang); skipped on resume",
        wav = f.getOrElse(4) { "" },
    )
    sink.write(row)
    System.err.println("RECOVERED: '$trackKey' died in native code on a previous launch; recording as failed.")
    return row
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
