package net.sigmabeta.chipbox.jvm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import net.sigmabeta.chipbox.player.generator.GeneratorEvent
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.memory.MemoryRepository
import net.sigmabeta.sage.logging.BasicHatchet
import net.sigmabeta.sage.logging.Hatchet
import java.io.File

private const val DEMO_TRACK_LENGTH_MS = 3_000L

/** Give the generator-event collector a moment to subscribe before the track starts. */
private const val SUBSCRIBE_GRACE_MS = 50L

/** Upper bound on producing the whole track, so a pipeline hang can't stall CI. */
private const val PRODUCE_TIMEOUT_MS = 60_000L

/** Let the consumer drain the last queued buffers before the WAV header is patched. */
private const val DRAIN_GRACE_MS = 1_000L

/**
 * Headless JVM entrypoint — the second target of the KMP migration. Drives the real player
 * pipeline (FakeEmulator → Generator → buffer manager → Speaker) with no Android dependency
 * and writes the synthesized PCM to a WAV file via [FileSpeaker].
 *
 * Proves the JVM target runs end-to-end against the now-multiplatform `player:common:api`.
 */
fun main(args: Array<String>) = runBlocking {
    val outputDir = File(args.firstOrNull() ?: System.getProperty("user.dir"))
    val hatchet = BasicHatchet()

    val repository = MemoryRepository()
    repository.addGame(demoGame())
    val track = (repository.getAllTracks().first { it is Data.Succeeded } as Data.Succeeded)
        .data
        .first()
    hatchet.i("Resolved track id=${track.id} '${track.title}' source='${track.source}'.")

    val bufferManager = RealBufferManager(hatchet)
    val generator = FakeGenerator(
        repository,
        ContentSourceRegistry(setOf(emptyContentSource(track.source))),
        bufferManager,
        hatchet,
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
    generator: FakeGenerator,
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

private fun demoGame() = RawGame(
    title = "KMP Demo",
    photoUrl = null,
    tracks = listOf(
        RawTrack(
            path = "demo.fake",
            source = "fake",
            title = "Sine Demo",
            artist = "Synth",
            game = "KMP Demo",
            length = DEMO_TRACK_LENGTH_MS,
            trackNumber = 1,
            fadeLengthMs = 0L,
            platform = Platform.OTHER,
        ),
    ),
)

/** FakeEmulator ignores the bytes; the source just has to resolve for `track.source`. */
private fun emptyContentSource(id: String) = object : ContentSource {
    override val sourceId: String = id
    override suspend fun openBytes(identifier: String): ByteArray = ByteArray(0)
}
