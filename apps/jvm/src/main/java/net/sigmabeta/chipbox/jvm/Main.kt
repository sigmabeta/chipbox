package net.sigmabeta.chipbox.jvm

import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.jvm.di.JvmChipboxGraph
import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.emulators.gba.GbaEmulator
import net.sigmabeta.chipbox.player.emulators.gme.GmeEmulator
import net.sigmabeta.chipbox.player.emulators.psf.PsfEmulator
import net.sigmabeta.chipbox.player.emulators.ssf.SsfEmulator
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import net.sigmabeta.chipbox.player.emulators.usf.UsfEmulator
import net.sigmabeta.chipbox.player.emulators.vgm.VgmEmulator
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.GeneratorEvent
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.logging.BasicHatchet
import net.sigmabeta.sage.logging.Hatchet
import java.io.File
import kotlin.system.exitProcess

/** Single-file mode: how much of the track to render (chiptunes loop forever). */
private const val DEMO_TRACK_LENGTH_MS = 10_000L

private const val DEMO_FADE_LENGTH_MS = 2_000L

/**
 * Every native emulator the legacy single-file mode can drive (the Metro graph for the
 * scan/play modes pulls them from [net.sigmabeta.chipbox.jvm.di.JvmEmulatorsModule]).
 */
private val ALL_EMULATORS: List<Emulator> = listOf(
    GbaEmulator,
    GmeEmulator,
    PsfEmulator,
    SsfEmulator,
    TwosfEmulator,
    UsfEmulator,
    VgmEmulator,
)

/** Give the generator-event collector a moment to subscribe before the track starts. */
private const val SUBSCRIBE_GRACE_MS = 50L

/** Upper bound on producing the whole track, so a pipeline hang can't stall CI. */
private const val PRODUCE_TIMEOUT_MS = 60_000L

/** Let the consumer drain the last queued buffers before the WAV header is patched. */
private const val DRAIN_GRACE_MS = 1_000L

/** Library DB + render staging live under .chipbox-jvm in the working directory. */
private const val WORK_DIR_NAME = ".chipbox-jvm"
private const val LIBRARY_DB_NAME = "library.sqlite"

private const val USAGE =
    """usage:
  gui                                open the Compose Multiplatform desktop window
  scan <music-dir>                   walk the dir, persist tracks/games to the library DB
  play <track-id|title-substring>    play a track from the library DB (writes WAV)
  <file-path> [output-dir]           single-file mode (no DB) — direct file -> WAV

Output: <output-dir>/Chipbox Output Files/temp.wav. Working dir is the
DB / render-cache home (.chipbox-jvm/library.sqlite + staging + pcm-cache)."""

/**
 * Headless / desktop JVM entrypoint. Four modes — `gui` opens the Compose Multiplatform
 * desktop window (bootstrap; see [runDesktop]); `scan` builds a real Room library by
 * walking a directory; `play` resolves a track from that library and renders it; the
 * legacy file-path form keeps working for one-off renders without touching the DB.
 *
 * The scan/play modes pull everything from the Metro [JvmChipboxGraph] — the JVM equivalent
 * of the Android app's `ChipboxAppGraph`. The legacy file-path mode bypasses the graph (no
 * DB, no scanner) and wires a small player pipeline manually so a CI smoke test doesn't need
 * a populated library on disk.
 *
 * `gui` is dispatched before `runBlocking` so the Compose event loop owns the main thread
 * cleanly — `application { Window { ... } }` blocks until the window closes, and the
 * coroutine machinery is irrelevant on that path.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "gui") {
        runDesktop(buildGraph(outputDir = workingDir()))
        return
    }
    runBlocking { dispatch(args) }
}

private suspend fun CoroutineScope.dispatch(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println(USAGE)
        exitProcess(2)
    }

    when (args[0]) {
        "scan" -> {
            require(args.size >= 2) { "scan mode: $USAGE" }
            val root = File(args[1])
            require(root.isDirectory) { "Not a directory: ${root.absolutePath}" }
            withGraph(outputDir = workingDir()) { graph ->
                graph.librarySource.addLocation(root)
                val scanner = graph.scanner
                scanner.startScan()
                // RealScanner runs the walk in its own scope; wait for a terminal state.
                scanner.state().first { it is ScannerState.Complete || it is ScannerState.Failed }
            }
        }

        "play" -> {
            require(args.size >= 2) { "play mode: $USAGE" }
            val outputDir = File(args.getOrNull(2) ?: System.getProperty("user.dir"))
            withGraph(outputDir = outputDir) { graph ->
                val track = resolveTrackFromLibrary(graph.repository, args[1], graph.hatchet)
                    ?: error("No track matches '${args[1]}' in the library. Did you `scan` first?")
                playPipelineFromGraph(track, graph)
            }
        }

        else -> playSingleFile(args, BasicHatchet())
    }
}

private fun workingDir(): File = File(System.getProperty("user.dir"))

/**
 * Build the Metro graph for this run. The DB file + render-cache workdir live under
 * `<user.dir>/.chipbox-jvm` so a run is self-contained; the WAV output dir comes from the
 * caller (CLI arg). Metro's `@SingleIn(AppScope::class)` makes accessors lazy enough that
 * `gui` mode can call this safely without opening the DB (it only touches `metroViewModelFactory`).
 */
internal fun buildGraph(outputDir: File): JvmChipboxGraph {
    val workDir = File(workingDir(), WORK_DIR_NAME).apply { mkdirs() }
    return createGraphFactory<JvmChipboxGraph.Factory>().create(
        dbPath = File(workDir, LIBRARY_DB_NAME).absolutePath,
        workDir = workDir,
        outputDir = outputDir,
    )
}

private suspend fun withGraph(outputDir: File, block: suspend (JvmChipboxGraph) -> Unit) {
    val graph = buildGraph(outputDir)
    graph.hatchet.i("Opened library DB at $WORK_DIR_NAME/$LIBRARY_DB_NAME")
    try {
        block(graph)
    } finally {
        // Tear down the graph-owned singletons that hold external resources. Room owns the
        // SQLite handle; the speaker is closed by playPipelineFromGraph on the play path.
        runCatching { (graph.repository as? AutoCloseable)?.close() }
    }
}

/**
 * Resolve a track by numeric id or a case-insensitive title substring. Substring match returns
 * the first hit ordered by [Track.title] — the same shape `getAllTracks` already produces.
 */
private suspend fun resolveTrackFromLibrary(
    repository: Repository,
    selector: String,
    hatchet: Hatchet,
): Track? {
    val asId = selector.toLongOrNull()
    val hit = if (asId != null) {
        repository.getTrack(asId, withGame = true, withArtists = true)
    } else {
        val all = repository.getAllTracks(withGame = true, withArtists = true)
            .first { it is Data.Succeeded || it is Data.Empty }
        val needle = selector.lowercase()
        (all as? Data.Succeeded)?.data?.firstOrNull { it.title.lowercase().contains(needle) }
    }
    if (hit != null) hatchet.i("Matched '${hit.title}' (id=${hit.id})")
    return hit
}

/** Play path using the Metro graph — pulls generator + speaker from the graph. */
private suspend fun CoroutineScope.playPipelineFromGraph(
    track: Track,
    graph: JvmChipboxGraph,
) {
    val hatchet = graph.hatchet
    val generator = graph.generator
    val speaker = graph.fileSpeaker

    playToCompletion(track, generator, speaker, hatchet)

    delay(DRAIN_GRACE_MS)
    launch { generator.stop() }
    speaker.stop()

    val outFile = File(File(File(System.getProperty("user.dir")), FileSpeaker.FOLDER_NAME), "temp.wav")
    hatchet.i("Done. WAV written to: ${outFile.absolutePath} (${outFile.length()} bytes).")
}

/**
 * Single-file legacy mode: no DB, no graph — wire a small player by hand around a
 * [SingleTrackRepository] so a CI smoke test doesn't need a populated library. Stages any
 * `*lib` siblings so mini-formats resolve their `_lib`.
 */
private suspend fun CoroutineScope.playSingleFile(args: Array<String>, hatchet: Hatchet) {
    val trackFile = File(args[0])
    val outputDir = File(args.getOrNull(1) ?: System.getProperty("user.dir"))
    require(trackFile.isFile) { "Track file not found: ${trackFile.absolutePath}" }

    val chainFiles = (trackFile.parentFile?.listFiles().orEmpty())
        .filter { it.isFile && it.extension.lowercase().endsWith("lib") }
        .map { ChainFile(filename = it.name, uri = it.absolutePath) }
    if (chainFiles.isNotEmpty()) {
        hatchet.i("Staging ${chainFiles.size} chain file(s): ${chainFiles.joinToString { it.filename }}")
    }

    val track = Track(
        id = 1L,
        path = trackFile.absolutePath,
        source = SOURCE_ID,
        title = trackFile.nameWithoutExtension,
        trackLengthMs = DEMO_TRACK_LENGTH_MS,
        trackNumber = 0,
        fadeLengthMs = DEMO_FADE_LENGTH_MS,
        game = null,
        artists = null,
        chainFiles = chainFiles,
        extension = trackFile.extension.lowercase(),
        platform = Platform.OTHER,
    )
    hatchet.i("Resolved track '${track.title}' (.${track.extension}) from ${track.path}.")

    val contentSources = ContentSourceRegistry(setOf(LocalFileContentSource()))
    val bufferManager = RealBufferManager(hatchet)
    val workDir = File(outputDir, WORK_DIR_NAME)
    val generator = RealGenerator(
        repository = SingleTrackRepository(track),
        contentSourceRegistry = contentSources,
        bufferManager = bufferManager,
        emulators = ALL_EMULATORS,
        stagingDir = File(workDir, "staging"),
        pcmCacheDir = File(workDir, "pcm-cache"),
        hatchet = hatchet,
    )
    val speaker = FileSpeaker(outputDir, hatchet, bufferManager)

    playToCompletion(track, generator, speaker, hatchet)

    delay(DRAIN_GRACE_MS)
    launch { generator.stop() }
    speaker.stop()

    val outFile = File(File(outputDir, FileSpeaker.FOLDER_NAME), "temp.wav")
    hatchet.i("Done. WAV written to: ${outFile.absolutePath} (${outFile.length()} bytes).")
}

/**
 * Mirror the Director's ordering: start the generator first, and only start the speaker's
 * consume loop once the generator's first [GeneratorEvent.Emitting] proves the sample rate is
 * set and buffers are flowing — starting the consumer earlier parks it on a pre-setSampleRate
 * channel that the buffer manager swaps out. [GeneratorEvent.TrackChange] = track fully
 * produced; [GeneratorEvent.Error] = the generator loop died. The collector subscribes before
 * startTrack so no early event is missed.
 */
private suspend fun CoroutineScope.playToCompletion(
    track: Track,
    generator: Generator,
    speaker: FileSpeaker,
    hatchet: Hatchet,
) {
    var speakerStarted = false
    val terminalEvent = async {
        generator.events().first { event ->
            when (event) {
                is GeneratorEvent.Error -> {
                    hatchet.e("Generator error: ${event.message}")
                    true
                }

                is GeneratorEvent.Emitting -> {
                    if (!speakerStarted) {
                        speakerStarted = true
                        speaker.play()
                    }
                    false
                }

                is GeneratorEvent.TrackChange -> true

                else -> false
            }
        }
    }
    delay(SUBSCRIBE_GRACE_MS)
    generator.startTrack(track.id)
    withTimeout(PRODUCE_TIMEOUT_MS) { terminalEvent.await() }
}
