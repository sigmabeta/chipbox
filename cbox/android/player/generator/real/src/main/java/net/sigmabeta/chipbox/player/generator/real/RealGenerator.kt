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

/**
 * Production [Generator] that picks a native [Emulator] by file extension.
 *
 * Every emulator the app knows about is injected as a list; the first one whose
 * [Emulator.isFileExtensionSupported] returns true wins. Native libraries are loaded lazily on
 * first use of a given emulator and remain loaded for the process lifetime.
 *
 * ### Cache staging
 * Native emulators expect a real filesystem path. Track bytes (which may come from any
 * [net.sigmabeta.chipbox.contentsource.ContentSource], including content URIs we can't seek
 * into directly) are written to `cacheDir/playback/track-{id}/main.{ext}` before the emulator
 * is pointed at them. PSF-style multi-file formats also have their auxiliary "chain files"
 * staged into the same directory so the native code can resolve them by relative path.
 */
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

    override suspend fun loadTrack(loadedTrack: Track, bytes: ByteArray) {
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

        val staged = stageToCache(loadedTrack, ext, bytes)
        emulator.hatchet = hatchet
        this.emulator = emulator
        emulator.loadTrack(loadedTrack.copy(path = staged.absolutePath))
    }

    private suspend fun stageToCache(track: Track, ext: String, mainBytes: ByteArray): File {
        val dir = File(context.cacheDir, "playback/track-${track.id}").apply {
            deleteRecursively()
            mkdirs()
        }
        val mainFile = File(dir, "main.$ext").apply { writeBytes(mainBytes) }

        if (track.chainFiles.isNotEmpty()) {
            val source = contentSourceRegistry.get(track.source)
            if (source == null) {
                hatchet.w("No content source '${track.source}' — chain files for track ${track.id} skipped.")
            } else {
                for (chain in track.chainFiles) {
                    val chainBytes = source.openBytes(chain.uri)
                        ?: error("Failed to read chain file '${chain.filename}' for track ${track.id}.")
                    File(dir, chain.filename).writeBytes(chainBytes)
                    hatchet.v("Staged chain file ${chain.filename} (${chainBytes.size} bytes).")
                }
            }
        }

        hatchet.v("Staged track ${track.id} to ${mainFile.absolutePath} (+${track.chainFiles.size} chain file(s)).")
        return mainFile
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
