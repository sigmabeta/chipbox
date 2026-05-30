package net.sigmabeta.chipbox.js.emulators

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import net.sigmabeta.chipbox.js.wasm.ChipboxVgmModule
import net.sigmabeta.chipbox.js.wasm.loadChipboxVgm
import net.sigmabeta.chipbox.js.wasm.VgmWasmCore
import net.sigmabeta.chipbox.player.emulators.Emulator
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * [Emulator] subclass that drives libvgm via the Emscripten-built `chipbox_vgm.wasm` module
 * (built by `:apps:js:buildVgmWasm`). The JS twin of `cbox/native/vgm/Vgm.cpp`'s JNI surface —
 * loads from a memory buffer, renders 16-bit stereo PCM at 44100 Hz.
 *
 * Same "read bytes back out of FakeFileSystem and push to WASM" pattern as [WasmGmeEmulator].
 * libvgm covers VGM + VGZ (the gzip-wrapped subset); the chipbox scanner emits both as
 * `Track.extension = "vgm" | "vgz"` so the registry matches either one here.
 */
class WasmVgmEmulator(private val fileSystem: FileSystem) : Emulator() {

    private var core: VgmWasmCore? = null
    private var lastErrorMessage: String? = null
    private var loadedSampleRate: Int = 44100

    override val supportedFileExtensions: List<String> = listOf(
        "vgm", // Video Game Music — uncompressed
        "vgz", // Video Game Music — gzip-wrapped (libvgm's MemoryLoader handles decompression)
    )

    override suspend fun ensureNativeLibReady() {
        if (loadedModule != null) return
        val pending = loadInProgress ?: loadScope.async { loadChipboxVgm() }.also { loadInProgress = it }
        loadedModule = pending.await()
    }

    override fun loadNativeLib() {
        val module = checkNotNull(loadedModule) {
            "WasmVgmEmulator.loadNativeLib() called before ensureNativeLibReady() — the " +
                "PcmTrackSource factory should have awaited it before reaching here."
        }
        core = VgmWasmCore(module)
    }

    override fun loadTrackInternal(path: String) {
        val core = checkNotNull(core) { "WasmVgmEmulator not initialized — loadNativeLib first." }
        val bytes = try {
            fileSystem.read(path.toPath()) { readByteArray() }
        } catch (t: Throwable) {
            lastErrorMessage = "Failed to read staged track from $path: ${t.message}"
            return
        }
        try {
            loadedSampleRate = core.loadTrack(bytes)
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

    override fun teardownInternal() {
        core?.teardown()
    }

    override fun getLastError(): String? = lastErrorMessage

    override fun getSampleRateInternal(): Int = loadedSampleRate

    companion object {
        private const val SHORTS_PER_FRAME = 2

        @Suppress("ObjectPropertyName")
        private var loadedModule: ChipboxVgmModule? = null

        @Suppress("ObjectPropertyName")
        private var loadInProgress: Deferred<ChipboxVgmModule>? = null

        @Suppress("OPT_IN_USAGE")
        private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
