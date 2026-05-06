package net.sigmabeta.chipbox.player.generator.fake

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.emulators.fake.FakeEmulator
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet

/**
 * Development-only [Generator] that bypasses all native emulators in favor of the in-process
 * [FakeEmulator] (sine/square synth driven by procedurally generated tracks). Useful for
 * exercising the pipeline without dragging in JNI dependencies.
 */
class FakeGenerator(
    repository: Repository,
    contentSourceRegistry: ContentSourceRegistry,
    bufferManager: ProducerBufferManager,
    hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Generator(repository, contentSourceRegistry, bufferManager, hatchet, dispatcher) {
    override fun getEmulatorSampleRate() = FakeEmulator.getSampleRateInternal()

    override suspend fun loadTrack(loadedTrack: Track, bytes: ByteArray) = FakeEmulator.loadTrack(loadedTrack)

    override fun generateAudio(buffer: ShortArray) = FakeEmulator.generateBuffer(buffer)

    override fun teardown() = FakeEmulator.teardown()

    override fun isTrackOver() = FakeEmulator.trackOver

    override fun getLastError() = FakeEmulator.getLastError()
}
