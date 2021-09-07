package net.sigmabeta.chipbox.player.generator.fake

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.emulators.fake.FakeEmulator
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.repository.Repository

class FakeGenerator(
    private val bufferSizeBytes: Int,
    private val repository: Repository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Generator(bufferSizeBytes, repository, dispatcher) {
    override val sampleRate = FakeEmulator.sampleRate

    override fun loadTrack(loadedTrack: Track) = FakeEmulator.loadTrack(loadedTrack)

    override fun generateAudio(buffer: ShortArray) = FakeEmulator.generateBuffer(buffer)

    override fun teardown() = FakeEmulator.teardown()

    override fun isTrackOver() = FakeEmulator.trackOver

    override fun getLastError() = FakeEmulator.getLastError()
}