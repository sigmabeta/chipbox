package net.sigmabeta.chipbox.js.emulators

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import net.sigmabeta.chipbox.js.wasm.ChipboxGmeModule
import net.sigmabeta.chipbox.js.wasm.GmeWasmCore
import net.sigmabeta.chipbox.js.wasm.loadChipboxGme
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
 * is a synchronous hook. [ensureNativeLibReady] (suspending) drives the load lazily on first
 * use — the factory calls it before constructing the source. Subsequent track loads short-circuit
 * once [loadedModule] is set. Concurrent first-uses dedupe via [loadInProgress] so we don't fire
 * the script-tag fetch twice.
 */
class WasmGmeEmulator(private val fileSystem: FileSystem) : Emulator() {

    private var core: GmeWasmCore? = null

    private var trackNumber: Int = 0
    private var lastErrorMessage: String? = null
    private var loadedSampleRate: Int = 44100

    override val supportedFileExtensions: List<String> = listOf(
        "gbs", // Game Boy Sound System
        "nsf", // NES Sound Format
        "nsfe", // NES Sound Format Extended
        "spc", // Super NES SPC700
    )

    override suspend fun ensureNativeLibReady() {
        if (loadedModule != null) return
        val pending = loadInProgress ?: loadScope.async { loadChipboxGme() }.also { loadInProgress = it }
        loadedModule = pending.await()
    }

    override fun loadNativeLib() {
        val module = checkNotNull(loadedModule) {
            "WasmGmeEmulator.loadNativeLib() called before ensureNativeLibReady() — the " +
                "PcmTrackSource factory should have awaited it before reaching here."
        }
        core = GmeWasmCore(module)
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

        // Cached module reference, populated by the first successful `loadChipboxGme()`. Held
        // statically so every WasmGmeEmulator instance shares the same WASM module — libgme is
        // one library; multiple Kotlin objects, one C-side singleton.
        @Suppress("ObjectPropertyName")
        private var loadedModule: ChipboxGmeModule? = null

        // Single-flight deferred for concurrent first-uses (e.g. user queues two GME tracks
        // back-to-back). Kotlin/JS is single-threaded, so a simple check-and-assign is safe.
        @Suppress("ObjectPropertyName")
        private var loadInProgress: Deferred<ChipboxGmeModule>? = null

        // Lifecycle-scoped to the app session — the load coroutines outlive any single
        // playback session and don't need cancellation tied to a particular Director call.
        @Suppress("OPT_IN_USAGE")
        private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
