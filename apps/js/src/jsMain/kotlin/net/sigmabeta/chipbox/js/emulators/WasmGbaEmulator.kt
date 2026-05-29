package net.sigmabeta.chipbox.js.emulators

import net.sigmabeta.chipbox.js.wasm.ChipboxGbaModule
import net.sigmabeta.chipbox.js.wasm.GbaWasmCore
import net.sigmabeta.chipbox.js.wasm.mirrorDirToMemfs
import net.sigmabeta.chipbox.player.emulators.Emulator
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * [Emulator] driving GSF (Game Boy Advance Sound Format) via the Emscripten-built
 * `chipbox_gba.wasm` — backed by mgba's full GBA core. PSF-family lifecycle.
 */
class WasmGbaEmulator(private val fileSystem: FileSystem) : Emulator() {

    private var core: GbaWasmCore? = null
    private var lastErrorMessage: String? = null

    override val supportedFileExtensions: List<String> = listOf(
        "gsf",      // GBA Sound Format
        "minigsf",  // GSF that depends on a `_lib.gsf` sibling
    )

    override fun loadNativeLib() {
        check(loadedModule != null) {
            "WasmGbaEmulator.loadNativeLib() called before the WASM module was set."
        }
        core = GbaWasmCore(loadedModule!!)
    }

    override fun loadTrackInternal(path: String) {
        val module = loadedModule
        val core = this.core
        if (module == null || core == null) {
            lastErrorMessage = "WasmGbaEmulator not initialized — loadNativeLib first."
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
            core.loadTrack(path)
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
        private var loadedModule: ChipboxGbaModule? = null

        fun setLoadedModule(module: ChipboxGbaModule) { loadedModule = module }
    }
}
