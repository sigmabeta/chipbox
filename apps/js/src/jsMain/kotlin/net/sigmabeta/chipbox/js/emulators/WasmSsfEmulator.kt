package net.sigmabeta.chipbox.js.emulators

import net.sigmabeta.chipbox.js.wasm.ChipboxSsfModule
import net.sigmabeta.chipbox.js.wasm.SsfWasmCore
import net.sigmabeta.chipbox.js.wasm.mirrorDirToMemfs
import net.sigmabeta.chipbox.player.emulators.Emulator
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * [Emulator] subclass that drives the SSF/DSF emulator via the Emscripten-built
 * `chipbox_ssf.wasm` module. Reuses the unmodified `cbox/native/ssf/Ssf.cpp` C API; the only
 * platform delta is how psflib's `fopen` calls resolve.
 *
 * Per-track flow:
 *   1. `RealPcmTrackSourceFactory` / `TrackStager` writes `main.ssf` + any `_lib*.ssf` chain
 *      files into [fileSystem] at `stagingTrackDir/`.
 *   2. `loadTrackInternal(path)` mirrors `stagingTrackDir`'s contents into the WASM module's
 *      MEMFS at the same paths via [mirrorDirToMemfs].
 *   3. C-side `loadFile(path)` runs unchanged — psflib's `fopen` finds main.ssf + chain
 *      siblings in MEMFS.
 */
class WasmSsfEmulator(private val fileSystem: FileSystem) : Emulator() {

    private var core: SsfWasmCore? = null
    private var lastErrorMessage: String? = null

    override val supportedFileExtensions: List<String> = listOf(
        "ssf",      // Sega Saturn Sound Format
        "minissf",  // SSF that depends on a `_lib.ssf` sibling
        "dsf",      // Sega Dreamcast Sound Format
        "minidsf",  // DSF that depends on a `_lib.dsf` sibling
    )

    override fun loadNativeLib() {
        check(loadedModule != null) {
            "WasmSsfEmulator.loadNativeLib() called before the WASM module was set. " +
                "JsMain must call WasmSsfEmulator.setLoadedModule(loadChipboxSsf()) before " +
                "audio playback starts."
        }
        core = SsfWasmCore(loadedModule!!)
    }

    override fun loadTrackInternal(path: String) {
        val module = loadedModule
        val core = this.core
        if (module == null || core == null) {
            lastErrorMessage = "WasmSsfEmulator not initialized — loadNativeLib first."
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
        private var loadedModule: ChipboxSsfModule? = null

        fun setLoadedModule(module: ChipboxSsfModule) { loadedModule = module }
    }
}
