package net.sigmabeta.chipbox.player.generator.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.emulators.real.PsfEmulator
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.repository.Repository

class RealGenerator(
    private val bufferSizeBytes: Int,
    private val repository: Repository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Generator(bufferSizeBytes, repository, dispatcher) {
    override val sampleRate = PsfEmulator.sampleRate

    override fun loadTrack(loadedTrack: Track) = PsfEmulator.loadTrack(loadedTrack)

    override fun generateAudio(buffer: ShortArray) = PsfEmulator.generateBuffer(buffer)

    override fun teardown() = PsfEmulator.teardown()

    override fun isTrackOver() = PsfEmulator.trackOver

    override fun getLastError() = PsfEmulator.getLastError()
}