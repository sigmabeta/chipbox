package net.sigmabeta.chipbox.player.generator.fake

import kotlinx.coroutines.CoroutineDispatcher
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.cache.PcmTrackSource
import net.sigmabeta.chipbox.player.emulators.fake.FakeEmulator
import net.sigmabeta.chipbox.player.generator.BaseGenerator
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.utils.ioDispatcher
import net.sigmabeta.sage.logging.Hatchet

/**
 * Development-only [Generator] that bypasses all native emulators in favor of the in-process
 * [FakeEmulator] (sine/square synth driven by procedurally generated tracks). Useful for
 * exercising the pipeline without dragging in JNI dependencies.
 *
 * The synth path skips the PCM cache entirely — there's no value in caching procedurally
 * generated output, and avoiding it keeps the harness self-contained.
 *
 * Named `SynthGenerator` rather than `FakeGenerator` to keep the `Fake*` prefix reserved for
 * test stubs (e.g. the `FakeGenerator` in `:fake` that records calls without doing any real
 * work). This class is a production runtime swap, not a test double — `Synth*` describes its
 * actual job.
 */
class SynthGenerator(
    repository: Repository,
    contentSourceRegistry: ContentSourceRegistry,
    bufferManager: ProducerBufferManager,
    hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = ioDispatcher
) : BaseGenerator(repository, contentSourceRegistry, bufferManager, hatchet, dispatcher) {

    override val pcmSourceFactory: PcmTrackSource.Factory = SynthPcmTrackSourceFactory(hatchet)
}

private class SynthPcmTrackSourceFactory(private val hatchet: Hatchet) : PcmTrackSource.Factory {
    override suspend fun open(track: Track, bytes: ByteArray): PcmTrackSource {
        FakeEmulator.hatchet = hatchet
        FakeEmulator.loadTrack(track)
        return SynthPcmTrackSource()
    }
}

private class SynthPcmTrackSource : PcmTrackSource {
    override val sampleRate: Int = FakeEmulator.getSampleRateInternal()

    override val totalFrames: Long? = null

    override val isOver: Boolean get() = FakeEmulator.trackOver

    override suspend fun readFrames(buffer: ShortArray): Int {
        val framesGenerated = FakeEmulator.generateBuffer(buffer)
        return if (framesGenerated < 0) 0 else framesGenerated
    }

    override suspend fun seek(framePosition: Long) {
        // Synth doesn't support seek.
    }

    override fun getLastError(): String? = FakeEmulator.getLastError()

    override suspend fun close() {
        FakeEmulator.teardown()
    }
}
