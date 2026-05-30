package net.sigmabeta.chipbox.js.emulators

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import net.sigmabeta.chipbox.js.wasm.ChipboxNcsfModule
import net.sigmabeta.chipbox.js.wasm.loadChipboxNcsf
import net.sigmabeta.chipbox.js.wasm.NcsfWasmCore
import net.sigmabeta.chipbox.js.wasm.mirrorDirToMemfs
import net.sigmabeta.chipbox.player.emulators.Emulator
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * [Emulator] driving the NCSF (NDS Nitro Composer / SSEQ Player) emulator via the
 * Emscripten-built `chipbox_ncsf.wasm`. PSF-family lifecycle — mirror staging dir into MEMFS,
 * call C `loadFile(path)`, render via `play`.
 */
class WasmNcsfEmulator(private val fileSystem: FileSystem) : Emulator() {

    private var core: NcsfWasmCore? = null
    private var lastErrorMessage: String? = null

    override val supportedFileExtensions: List<String> = listOf(
        "ncsf", // Nitro Composer Sound Format
        "minincsf", // NCSF that depends on a `_lib.ncsf` sibling
    )

    override suspend fun ensureNativeLibReady() {
        if (loadedModule != null) return
        val pending = loadInProgress ?: loadScope.async { loadChipboxNcsf() }.also { loadInProgress = it }
        loadedModule = pending.await()
    }

    override fun loadNativeLib() {
        val module = checkNotNull(loadedModule) {
            "WasmNcsfEmulator.loadNativeLib() called before ensureNativeLibReady() — the " +
                "PcmTrackSource factory should have awaited it before reaching here."
        }
        core = NcsfWasmCore(module)
    }

    override fun loadTrackInternal(path: String) {
        val module = loadedModule
        val core = this.core
        if (module == null || core == null) {
            lastErrorMessage = "WasmNcsfEmulator not initialized — loadNativeLib first."
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

    override fun teardownInternal() {
        core?.teardown()
    }

    override fun getLastError(): String? = lastErrorMessage

    override fun getSampleRateInternal(): Int = core?.sampleRate() ?: 44100

    companion object {
        private const val SHORTS_PER_FRAME = 2

        @Suppress("ObjectPropertyName")
        private var loadedModule: ChipboxNcsfModule? = null

        @Suppress("ObjectPropertyName")
        private var loadInProgress: Deferred<ChipboxNcsfModule>? = null

        @Suppress("OPT_IN_USAGE")
        private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
