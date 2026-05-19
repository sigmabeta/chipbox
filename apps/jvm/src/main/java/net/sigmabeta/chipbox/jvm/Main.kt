package net.sigmabeta.chipbox.jvm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
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
import net.sigmabeta.sage.logging.BasicHatchet
import net.sigmabeta.sage.logging.Hatchet
import java.io.File
import kotlin.system.exitProcess

/** How much of the track to render. SPC has no end-of-file; the emulator loops forever. */
private const val DEMO_TRACK_LENGTH_MS = 10_000L

private const val DEMO_FADE_LENGTH_MS = 2_000L

private const val SOURCE_ID = "file"

/**
 * Every native emulator the JVM target can drive. [RealGenerator]'s factory picks one per
 * track by file extension, so the whole set is always wired and any supported file just works.
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

/**
 * Headless JVM entrypoint. Drives the *real* native player pipeline
 * (native [Emulator] → RealGenerator/render-ahead cache → buffer manager → Speaker) with no
 * Android dependency and writes the synthesized PCM to a WAV file via [FileSpeaker].
 *
 * `args[0]` (required) = path to any file one of [ALL_EMULATORS] supports
 * (spc/nsf/gsf/psf/ssf/usf/2sf/vgm/…); the generator picks the emulator by extension.
 * `args[1]` (optional) = output directory (default: cwd).
 *
 * Proves a native emulator runs end-to-end on the JVM target — the matching `.so` is
 * host-built into `apps/jvm/libs` and loaded via `System.loadLibrary(...)`.
 */
fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        System.err.println(
            "usage: <track-file> [output-dir]\n" +
                "  track-file  a file a wired emulator supports " +
                "(.spc .nsf .gbs .psf .minipsf .psf2 .vgm .vgz .usf .miniusf " +
                ".2sf .mini2sf .gsf .minigsf .ssf .dsf)\n" +
                "  output-dir  where to write 'Chipbox Output Files/temp.wav' " +
                "(default: current directory)"
        )
        exitProcess(2)
    }

    val trackFile = File(args[0])
    val outputDir = File(args.getOrNull(1) ?: System.getProperty("user.dir"))
    val hatchet = BasicHatchet()

    require(trackFile.isFile) { "Track file not found: ${trackFile.absolutePath}" }

    // mini-formats (minipsf/minigsf/miniusf/mini2sf/minissf) embed a `_lib` tag the native
    // loader resolves as a sibling of the staged main file. Stage every `*lib` neighbour
    // (gsflib/psflib/usflib/2sflib/ssflib/…) so those formats resolve without a real library.
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

    val repository = SingleTrackRepository(track)
    val contentSources = ContentSourceRegistry(setOf(FileContentSource(SOURCE_ID)))
    val bufferManager = RealBufferManager(hatchet)

    // Render-ahead staging + PCM cache live under the output dir so a run is self-contained.
    val workDir = File(outputDir, ".chipbox-jvm")
    val generator = RealGenerator(
        repository = repository,
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
