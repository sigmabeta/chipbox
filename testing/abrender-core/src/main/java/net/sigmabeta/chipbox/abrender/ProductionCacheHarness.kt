package net.sigmabeta.chipbox.abrender

import java.io.File
import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.readers.PsfReader
import net.sigmabeta.sage.logging.Hatchet

private const val MILLIS_PER_SECOND = 1000L
private const val TRACK_NUMBER = 0

/**
 * Thin counting decorator around a real production backend. It forwards every call to [delegate]
 * and only records native track-load / teardown calls, so the production-cache harnesses
 * (`perfbench cache` on the host, `DeviceCacheBenchmark` on a device, and the cache factory unit
 * test) measure the real path rather than a fake emulator.
 *
 * One home on purpose: the counting surface is identical everywhere, and a divergent copy is how
 * a harness starts measuring something the app never runs.
 */
class CountingEmulator(private val delegate: Emulator) : Emulator() {
    var loads: Int = 0
        private set

    var teardowns: Int = 0
        private set

    fun reset() {
        loads = 0
        teardowns = 0
    }

    override fun loadNativeLib() = delegate.loadNativeLib()

    override suspend fun ensureNativeLibReady() = delegate.ensureNativeLibReady()

    override fun loadTrackInternal(path: String) {
        loads += 1
        delegate.loadTrackInternal(path)
    }

    override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int =
        delegate.generateBufferInternal(buffer, framesPerBuffer)

    override fun teardownInternal() {
        teardowns += 1
        delegate.teardownInternal()
    }

    override fun getLastError(): String? = delegate.getLastError()

    override fun getDiagnostics(): String? = delegate.getDiagnostics()

    override fun getSampleRateInternal(): Int = delegate.getSampleRateInternal()

    // Delegated so a fixed-rate backend (TwosfEmulator) actually takes the factory's warm-hit
    // path instead of falling back to native rate negotiation.
    override val knownSampleRate: Int? get() = delegate.knownSampleRate

    override val supportedFileExtensions: List<String> = delegate.supportedFileExtensions

    override fun setTrackNumber(number: Int) = delegate.setTrackNumber(number)
}

/**
 * Builds the production [Track] for a corpus file and returns it with the bytes to hand to a
 * `PcmTrackSource.Factory`. 2SF `_lib` siblings referenced by the PSF tag are resolved into
 * staging chain files exactly as the scanner does, and [seconds] becomes the render budget so
 * cold and warm passes are bounded and comparable across tracks.
 */
fun productionTrack(
    id: Long,
    selected: SelectedTrack,
    seconds: Int,
    hatchet: Hatchet,
): Pair<Track, ByteArray> {
    val file = File(selected.path)
    val bytes = file.readBytes()
    val tagInfo = PsfReader(hatchet).readTagInfo(bytes)
    val chainFiles = tagInfo?.libReferences.orEmpty().mapNotNull { reference ->
        File(file.parentFile, reference)
            .takeIf { it.isFile }
            ?.let { ChainFile(filename = it.name, uri = it.absolutePath) }
    }
    val track = Track(
        id = id,
        path = file.absolutePath,
        source = "file",
        title = selected.title,
        trackLengthMs = seconds * MILLIS_PER_SECOND,
        trackNumber = TRACK_NUMBER,
        fadeLengthMs = 0L,
        game = null,
        artists = null,
        chainFiles = chainFiles,
        extension = selected.extension,
        platform = Platform.NDS,
    )
    return track to bytes
}
