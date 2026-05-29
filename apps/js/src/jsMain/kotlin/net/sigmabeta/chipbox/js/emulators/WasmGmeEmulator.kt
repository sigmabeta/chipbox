package net.sigmabeta.chipbox.js.emulators

import net.sigmabeta.chipbox.js.wasm.ChipboxGmeModule
import net.sigmabeta.chipbox.js.wasm.GmeWasmCore
import net.sigmabeta.chipbox.player.emulators.Emulator
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * [Emulator] subclass that drives libgme via the Emscripten-built `chipbox_gme.wasm` module
 * (built by `:apps:js:buildGmeWasm`). The JS twin of
 * `cbox/common/player/emulators/gme/real/src/main/java/.../GmeEmulator.kt`.
 *
 * The chipbox Emulator contract is path-based: `loadTrackInternal(path: String)` hands the
 * native side a filesystem path it can `fopen`. There's no real filesystem in the browser, but
 * the chipbox pipeline already routes through `okio.FileSystem` (we wire `FakeFileSystem` in
 * `WebModules`) — and the input staging in `RealPcmTrackSourceFactory` writes the source bytes
 * to that filesystem before handing the path here. We just read them back out via the same
 * okio instance and push them into the WASM heap via [GmeWasmCore.loadTrack].
 *
 * The trip through okio is a wasted copy on the order of one chiptune file (KB to a few MB),
 * which is a fine cost to pay for reusing the entire RealGenerator + RealPcmTrackSourceFactory
 * + BaseGenerator chain unchanged.
 *
 * #### Native-lib lifecycle
 * The WASM module load is async (script tag + `chipboxGme()` promise) but [Emulator.loadNativeLib]
 * is a synchronous hook. Resolution: `JsMain` calls [setLoadedModule] from inside the startup
 * coroutine *before* the graph hands the emulator to anything that might play audio. If
 * `loadNativeLib` runs before the module is set, it throws — that's a wiring bug.
 */
class WasmGmeEmulator(private val fileSystem: FileSystem) : Emulator() {

    private var core: GmeWasmCore? = null

    private var trackNumber: Int = 0
    private var lastErrorMessage: String? = null
    private var loadedSampleRate: Int = 44100

    override val supportedFileExtensions: List<String> = listOf(
        "gbs",      // Game Boy Sound System
        "nsf",      // NES Sound Format
        "nsfe",     // NES Sound Format Extended
        "spc",      // Super NES SPC700
    )

    override fun loadNativeLib() {
        check(loadedModule != null) {
            "WasmGmeEmulator.loadNativeLib() called before the WASM module was set. " +
                "JsMain must call WasmGmeEmulator.setLoadedModule(loadChipboxGme()) before " +
                "audio playback starts."
        }
        core = GmeWasmCore(loadedModule!!)
    }

    override fun setTrackNumber(number: Int) {
        trackNumber = number
    }

    override fun loadTrackInternal(path: String) {
        val core = checkNotNull(core) { "WasmGmeEmulator not initialized — loadNativeLib first." }
        val bytes = try {
            fileSystem.read(path.toPath()) { readByteArray() }
        } catch (t: Throwable) {
            lastErrorMessage = "Failed to read staged track from $path: ${t.message}"
            return
        }
        try {
            loadedSampleRate = core.loadTrack(bytes, trackNumber)
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
        // BaseGenerator hands a buffer sized 2*frames; copy the freshly-rendered samples in.
        val toCopy = minOf(pcm.size, buffer.size)
        pcm.copyInto(buffer, 0, 0, toCopy)
        return toCopy / SHORTS_PER_FRAME
    }

    override fun teardownInternal() {
        core?.teardown()
    }

    override fun getLastError(): String? = lastErrorMessage

    override fun getSampleRateInternal(): Int = loadedSampleRate

    companion object {
        private const val SHORTS_PER_FRAME = 2

        // Cached module reference, populated by JsMain after the startup `loadChipboxGme()`
        // completes. Held statically so every WasmGmeEmulator instance shares the same WASM
        // module — libgme is one library; multiple JS-side objects, one C-side singleton.
        @Suppress("ObjectPropertyName")
        private var loadedModule: ChipboxGmeModule? = null

        /**
         * Set the WASM module reference. Called from JsMain after the async `loadChipboxGme()`
         * resolves. Safe to call repeatedly — overwriting with the same module is a no-op.
         */
        fun setLoadedModule(module: ChipboxGmeModule) {
            loadedModule = module
        }
    }
}
