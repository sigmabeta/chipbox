package net.sigmabeta.chipbox.js.emulators

import net.sigmabeta.chipbox.js.wasm.ChipboxPsfModule
import net.sigmabeta.chipbox.js.wasm.PsfWasmCore
import net.sigmabeta.chipbox.js.wasm.mirrorDirToMemfs
import net.sigmabeta.chipbox.player.emulators.Emulator
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * [Emulator] subclass driving slopsf (PSF1/PSF2 HLE) via the Emscripten-built
 * `chipbox_psf.wasm` module. PSX and PS2 both route here — slopsf sniffs the version internally.
 *
 * Lifecycle matches [WasmSsfEmulator] / [WasmUsfEmulator] — mirror the staged track dir into
 * the WASM module's MEMFS, then call the path-based C `loadFile`. PSF2's directory-structured
 * wave-bank refs work because `mirrorDirToMemfs` walks the staging dir as `TrackStager` lays
 * it out (main file + flat `_lib*` siblings).
 */
class WasmPsfEmulator(private val fileSystem: FileSystem) : Emulator() {

    private var core: PsfWasmCore? = null
    private var lastErrorMessage: String? = null

    override val supportedFileExtensions: List<String> = listOf(
        "psf",      // PlayStation Sound Format (PSX)
        "minipsf",  // PSF that depends on a `_lib.psf` sibling
        "psf2",     // PlayStation 2 Sound Format
        "minipsf2", // PSF2 that depends on a `_lib.psf2` sibling
    )

    override fun loadNativeLib() {
        check(loadedModule != null) {
            "WasmPsfEmulator.loadNativeLib() called before the WASM module was set."
        }
        core = PsfWasmCore(loadedModule!!)
    }

    override fun loadTrackInternal(path: String) {
        val module = loadedModule
        val core = this.core
        if (module == null || core == null) {
            lastErrorMessage = "WasmPsfEmulator not initialized — loadNativeLib first."
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
        private var loadedModule: ChipboxPsfModule? = null

        fun setLoadedModule(module: ChipboxPsfModule) { loadedModule = module }
    }
}
