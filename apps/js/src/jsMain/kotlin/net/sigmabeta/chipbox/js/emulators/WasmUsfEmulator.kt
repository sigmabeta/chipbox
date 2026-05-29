package net.sigmabeta.chipbox.js.emulators

import net.sigmabeta.chipbox.js.wasm.ChipboxUsfModule
import net.sigmabeta.chipbox.js.wasm.UsfWasmCore
import net.sigmabeta.chipbox.js.wasm.mirrorDirToMemfs
import net.sigmabeta.chipbox.player.emulators.Emulator
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * [Emulator] subclass that drives the lazyusf2 N64 emulator via the Emscripten-built
 * `chipbox_usf.wasm` module. See [WasmSsfEmulator] for the shared PSF-family lifecycle —
 * MEMFS mirror → C-side `loadFile(path)` → render → teardown.
 *
 * Note: the cached interpreter is ~5-10x slower than the native x86_64 dynarec path on JVM
 * but still well under realtime for the USF library. If a track underruns, look here first.
 */
class WasmUsfEmulator(private val fileSystem: FileSystem) : Emulator() {

    private var core: UsfWasmCore? = null
    private var lastErrorMessage: String? = null

    override val supportedFileExtensions: List<String> = listOf(
        "usf",      // Ultra Sound Format (Nintendo 64)
        "miniusf",  // USF that depends on a `_lib.usf` sibling
    )

    override fun loadNativeLib() {
        check(loadedModule != null) {
            "WasmUsfEmulator.loadNativeLib() called before the WASM module was set."
        }
        core = UsfWasmCore(loadedModule!!)
    }

    override fun loadTrackInternal(path: String) {
        val module = loadedModule
        val core = this.core
        if (module == null || core == null) {
            lastErrorMessage = "WasmUsfEmulator not initialized — loadNativeLib first."
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
        private var loadedModule: ChipboxUsfModule? = null

        fun setLoadedModule(module: ChipboxUsfModule) { loadedModule = module }
    }
}
