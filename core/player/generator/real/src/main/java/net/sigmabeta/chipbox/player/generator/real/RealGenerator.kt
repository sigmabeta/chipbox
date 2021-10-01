package net.sigmabeta.chipbox.player.generator.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.repository.Repository
import timber.log.Timber
import java.io.File

class RealGenerator(
    repository: Repository,
    bufferManager: ProducerBufferManager,
    private val emulators: List<Emulator>,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Generator(repository, bufferManager, dispatcher) {
    private var emulator: Emulator? = null

    override fun loadTrack(loadedTrack: Track) {
        if (emulator != null) {
            teardown()
        }

        val emulator = getSupportedEmulator(loadedTrack.path)
            ?: throw IllegalArgumentException("No emulator found for this file type.")

        if (!emulator.nativeLibLoaded) {
            emulator.loadNativeLib()
            emulator.nativeLibLoaded = true
            println("Loading native lib for supported emulator: ${emulator.javaClass.simpleName}")
        }

        this.emulator = emulator
        emulator.loadTrack(loadedTrack)
    }

    private fun getSupportedEmulator(path: String): Emulator? {
        val extension = File(path).extension
        return emulators.firstOrNull {
            it.isFileExtensionSupported(extension)
        }
    }

    override fun generateAudio(buffer: ShortArray) = ifNotNull(emulator) { generateBuffer(buffer) }

    override fun getEmulatorSampleRate() = ifNotNull(emulator) { getSampleRateInternal() }

    override fun teardown() = ifNotNull(emulator) { teardown() }

    override fun isTrackOver() = ifNotNull(emulator) { trackOver }

    override fun getLastError() = ifNotNull(emulator) { getLastError() }

    private fun <Return> ifNotNull(
        emulator: Emulator?,
        action: Emulator.() -> Return
    ) = ifNotNull(emulator, null) {
        action()
    }

    private fun <Argument, Return> ifNotNull(
        emulator: Emulator?,
        argument: Argument,
        action: Emulator.(Argument) -> Return
    ): Return {
        if (emulator == null) {
            throw IllegalStateException("No emulator loaded.")
        }

        return emulator.action(argument)
    }
}