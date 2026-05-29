package net.sigmabeta.chipbox.js.emulators

import net.sigmabeta.chipbox.js.wasm.ChipboxVgmstreamModule
import net.sigmabeta.chipbox.js.wasm.VgmstreamWasmCore
import net.sigmabeta.chipbox.js.wasm.mirrorDirToMemfs
import net.sigmabeta.chipbox.player.emulators.Emulator
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * [Emulator] driving vgmstream via the Emscripten-built `chipbox_vgmstream.wasm`. Decodes
 * streamed game audio (ADX, HCA, BRSTM, STRM, FSB, ...). Different category from the chiptune
 * synthesizers — same lifecycle though (mirror staging dir to MEMFS, path-based load), with one
 * twist: the track number is passed in as the subsong index (vgmstream containers like FSB pack
 * many streams per file).
 *
 * Like the JVM `VgmstreamEmulator`, [supportedFileExtensions] is sourced from the native library
 * (lazy first access; JsMain guarantees the WASM module is resolved before any Emulator method
 * runs). Registered last in the emulator list so dedicated chiptune emulators win shared
 * extensions.
 */
class WasmVgmstreamEmulator(private val fileSystem: FileSystem) : Emulator() {

    private var core: VgmstreamWasmCore? = null
    private var lastErrorMessage: String? = null
    private var trackNumber: Int = 0

    override val supportedFileExtensions: List<String> by lazy {
        val module = loadedModule
            ?: error("WasmVgmstreamEmulator.supportedFileExtensions read before module loaded.")
        VgmstreamWasmCore(module).supportedExtensions()
    }

    override fun loadNativeLib() {
        check(loadedModule != null) {
            "WasmVgmstreamEmulator.loadNativeLib() called before the WASM module was set."
        }
        core = VgmstreamWasmCore(loadedModule!!)
    }

    override fun setTrackNumber(number: Int) {
        trackNumber = number
    }

    override fun loadTrackInternal(path: String) {
        val module = loadedModule
        val core = this.core
        if (module == null || core == null) {
            lastErrorMessage = "WasmVgmstreamEmulator not initialized — loadNativeLib first."
            return
        }
        val mainPath = path.toPath()
        val stagingDir = mainPath.parent ?: run {
            lastErrorMessage = "Staged path has no parent dir: $path"
            return
        }
        try {
            mirrorDirToMemfs(fileSystem, stagingDir, module.FS)
        } catch (t: Throwable) {
            lastErrorMessage = "Failed to mirror staging dir to MEMFS: ${t.message}"
            return
        }
        try {
            core.loadTrack(path, trackNumber)
            lastErrorMessage = null
        } catch (t: Throwable) {
            lastErrorMessage = t.message ?: t::class.simpleName
        }
    }

    override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int): Int {
        val core = core ?: return 0
        val pcm = try {
            core.play(framesPerBuffer)
        } catch (t: Throwable) {
            lastErrorMessage = t.message ?: t::class.simpleName
            return 0
        } ?: return 0
        val toCopy = minOf(pcm.size, buffer.size)
        pcm.copyInto(buffer, 0, 0, toCopy)
        return toCopy / SHORTS_PER_FRAME
    }

    override fun teardownInternal() { core?.teardown() }

    override fun getLastError(): String? = lastErrorMessage

    override fun getSampleRateInternal(): Int = core?.sampleRate() ?: 44100

    companion object {
        private const val SHORTS_PER_FRAME = 2

        @Suppress("ObjectPropertyName")
        private var loadedModule: ChipboxVgmstreamModule? = null

        fun setLoadedModule(module: ChipboxVgmstreamModule) { loadedModule = module }
    }
}
