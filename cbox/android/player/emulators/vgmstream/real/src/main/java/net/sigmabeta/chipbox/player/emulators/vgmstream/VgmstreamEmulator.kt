package net.sigmabeta.chipbox.player.emulators.vgmstream

import net.sigmabeta.chipbox.player.emulators.Emulator

/**
 * Wraps the vendored vgmstream library, which decodes hundreds of *streamed* (prerecorded) game
 * audio formats (ADX, HCA, NGC_DSP/BRSTM, STRM, FSB, the IMA/MSADPCM/PSX/XA families, ...) — a
 * different category from chipbox's synthesized chiptune emulators.
 *
 * The supported-extension set is sourced from vgmstream itself (minus generic formats like
 * wav/ogg/mp3). This emulator is registered LAST so the dedicated chiptune emulators win any
 * shared extension. Many vgmstream formats pack multiple subsongs per file; the subsong index
 * rides in as the track number (like [net.sigmabeta.chipbox.player.emulators.gme.GmeEmulator]).
 */
object VgmstreamEmulator : Emulator() {
    override fun loadNativeLib() {
        System.loadLibrary("vgmstream")
    }

    // Lazily queried from the native library (which is the single source of truth). Loading the
    // library here is idempotent with the generator's own lazy load.
    override val supportedFileExtensions: List<String> by lazy {
        loadNativeLib()
        getSupportedExtensionsInternal().toList()
    }

    private var trackNumber: Int = 0

    override fun setTrackNumber(number: Int) {
        this.trackNumber = number
    }

    override fun loadTrackInternal(path: String) {
        hatchet.d("Starting vgmstream subsong $trackNumber from $path")
        loadTrackInternalWithNumber(path, trackNumber)
    }

    external override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int

    external override fun teardownInternal()

    external override fun getLastError(): String?

    external override fun getSampleRateInternal(): Int

    private external fun loadTrackInternalWithNumber(path: String, trackNumber: Int)

    private external fun getSupportedExtensionsInternal(): Array<String>
}
