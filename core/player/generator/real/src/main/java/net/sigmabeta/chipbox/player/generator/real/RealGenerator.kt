package net.sigmabeta.chipbox.player.generator.real

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet
import java.io.File

class RealGenerator(
    repository: Repository,
    contentSourceRegistry: ContentSourceRegistry,
    bufferManager: ProducerBufferManager,
    private val emulators: List<Emulator>,
    private val context: Context,
    hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Generator(repository, contentSourceRegistry, bufferManager, hatchet, dispatcher) {
    private var emulator: Emulator? = null

    override fun loadTrack(loadedTrack: Track, bytes: ByteArray) {
        if (emulator != null) {
            teardown()
        }

        val ext = loadedTrack.path.substringAfterLast('.', "").lowercase()
        hatchet.d("Loading track: ${loadedTrack.title} (.$ext)")

        val emulator = emulators.firstOrNull { it.isFileExtensionSupported(ext) }
            ?: throw IllegalArgumentException("No emulator found for extension '$ext'.")

        if (!emulator.nativeLibLoaded) {
            hatchet.i("Loading native lib for ${emulator::class.simpleName}.")
            emulator.loadNativeLib()
            emulator.nativeLibLoaded = true
        }

        val staged = stageToCache(loadedTrack.id, ext, bytes)
        emulator.hatchet = hatchet
        this.emulator = emulator
        emulator.loadTrack(loadedTrack.copy(path = staged.absolutePath))
    }

    private fun stageToCache(trackId: Long, ext: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "playback").apply { mkdirs() }
        val file = File(dir, "track-$trackId.$ext").apply { writeBytes(bytes) }
        hatchet.v("Staged track $trackId to ${file.absolutePath}.")
        return file
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
