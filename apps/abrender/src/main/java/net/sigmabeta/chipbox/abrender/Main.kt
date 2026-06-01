package net.sigmabeta.chipbox.abrender

import java.io.File

private const val DEFAULT_SECONDS = 30
private const val DEFAULT_TOP_N = 20
private const val MILLIS_PER_SECOND = 1000L
private const val DEFAULT_MAX_WALL_SECONDS = 120L
private const val DEFAULT_MAX_WALL_MILLIS = DEFAULT_MAX_WALL_SECONDS * MILLIS_PER_SECOND

/**
 * The A/B render harness. Native-emulator A/B can't load two `.so` versions in one JVM, so it's a
 * two-pass workflow: render a labelled baseline, rebuild the native lib, render a labelled candidate,
 * then `diff` the two runs.
 */
fun main(rawArgs: Array<String>) {
    val command = rawArgs.firstOrNull()
    if (command == null || command in setOf("-h", "--help", "help")) {
        printUsage()
        return
    }
    val opts = parseOptions(rawArgs.drop(1))

    when (command) {
        "render" -> runRender(buildArgs(command, opts))

        "diff" -> runDiff(buildArgs(command, opts))

        else -> {
            println("Unknown command: $command\n")
            printUsage()
        }
    }
}

private fun buildArgs(command: String, opts: Map<String, String>): Args {
    val outDir = File(opts["out"] ?: "ab-runs")
    return Args(
        command = command,
        label = opts["label"] ?: "run",
        seconds = opts["seconds"]?.toIntOrNull() ?: DEFAULT_SECONDS,
        outDir = outDir,
        corpusDir = File(opts["dir"] ?: "."),
        extensions = opts["ext"]
            ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet(),
        gameFilter = opts["game"],
        everySong = opts.containsKey("every-song"),
        subsong = opts["subsong"]?.toIntOrNull() ?: 0,
        limit = opts["limit"]?.toIntOrNull(),
        overwrite = opts.containsKey("overwrite"),
        maxWallMillis = opts["max-wall-seconds"]?.toLongOrNull()?.times(MILLIS_PER_SECOND)
            ?: DEFAULT_MAX_WALL_MILLIS,
        shards = opts["shards"]?.toIntOrNull() ?: 1,
        shard = opts["shard"]?.toIntOrNull() ?: 0,
        runA = opts["a"],
        runB = opts["b"],
        topN = opts["top"]?.toIntOrNull() ?: DEFAULT_TOP_N,
    ).also { validate(it) }
}

private fun validate(args: Args) {
    if (args.command == "diff") {
        require(args.runA != null && args.runB != null) { "diff requires --a <run> and --b <run>" }
    }
    if (args.command == "render") {
        require(args.corpusDir.isDirectory) {
            "Corpus directory not found: ${args.corpusDir.absolutePath}\nPass --dir <folder of audio files>."
        }
        require(args.shards >= 1) { "--shards must be >= 1" }
        require(args.shard in 0 until args.shards) { "--shard must be in 0..${args.shards - 1}" }
    }
}

/**
 * Parses `--key value` and bare `--flag` options. A token starting with `--` followed by another
 * `--` token (or end of input) is treated as a boolean flag; otherwise it consumes the next token.
 */
private fun parseOptions(tokens: List<String>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        require(token.startsWith("--")) { "Unexpected argument '$token' (options must start with --)" }
        val key = token.removePrefix("--")
        val next = tokens.getOrNull(i + 1)
        if (next == null || next.startsWith("--")) {
            out[key] = "true"
            i += 1
        } else {
            out[key] = next
            i += 2
        }
    }
    return out
}

private fun printUsage() {
    println(
        """
        chipbox-abrender — A/B render harness for emulator-backend changes.

        Native A/B is two-pass (one JVM can't load two .so versions):
          1) render a baseline       2) rebuild the native lib       3) render a candidate       4) diff

        Example (USF sync, one song per game, 30s each):
          ./gradlew :apps:jvm:nativeLibs                                  # build current libs
          ./gradlew :apps:abrender:run --args="render --dir /corpus/usf --label baseline --ext usf,miniusf"
          # ... apply the native change, then rebuild and render again ...
          ./gradlew :apps:abrender:run --args="render --dir /corpus/usf --label candidate --ext usf,miniusf"
          ./gradlew :apps:abrender:run --args="diff --a baseline --b candidate"

        render --dir <folder> [options]
          --dir <folder>       corpus root to walk for audio files            (required)
          --label <name>       run name; output goes to <out>/<name>/          (default: run)
          --seconds <n>        seconds of audio per track                      (default: $DEFAULT_SECONDS)
          --ext <a,b>          only these extensions, e.g. usf,miniusf         (default: all supported)
          --game <substr>      only files whose parent folder contains <substr>
          --every-song         every playable file (default: one per folder/game)
          --subsong <n>        subsong index for multi-song formats            (default: 0)
          --limit <n>          cap the number of tracks (quick checks)
          --overwrite          re-render tracks already present                (default: resume/skip)
          --max-wall-seconds   per-track wall-clock cap                        (default: $DEFAULT_MAX_WALL_SECONDS)
          --shards <n>         partition the corpus into n disjoint slices     (default: 1)
          --shard <i>          render only slice i (0-based) of --shards        (default: 0)
          --out <dir>          runs root                                       (default: ab-runs)

        Parallelism: one JVM can't render concurrently (singleton native state per backend), so run
        n processes — one per logical core — each over a disjoint --shard of the corpus. A track that
        wedges in an uninterruptible native call only halts its own shard; a watchdog records it as
        WEDGED and exits so a relaunch resumes past it. See render-parallel.sh.

        diff --a <run> --b <run> [options]
          --a <run>            baseline run (label under <out>, or a path)
          --b <run>            candidate run (label under <out>, or a path)
          --top <n>            how many most-changed tracks to list            (default: $DEFAULT_TOP_N)
          --out <dir>          runs root                                       (default: ab-runs)

        Metric: diff reports RMS of the sample-wise difference A−B (dBFS; -inf = bit-identical),
        plus aggregate ΔRMS. Per-track WAVs under <out>/<run>/wav/ are the listenable artifacts.
        """.trimIndent(),
    )
}
