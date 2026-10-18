package net.sigmabeta.chipbox.player.emulators

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Regression coverage for the browser's vgmstream backend, whose supported-extension list is only
 * readable after its WASM module loads.
 *
 * [EmulatorSelector] must keep the DI order: a dedicated emulator wins any shared extension and the
 * lazy catch-all is never probed for it. The first vgmstream-only extension loads the module once,
 * and a failed load surfaces as an error rather than "no emulator found".
 *
 * The documented browser order this mirrors is
 * `gme, vgm, ssf, usf, psf, ncsf, twosf, gba, vgmstream` (apps/js di/WebModules.kt), with
 * vgmstream last for exactly this reason.
 */
class EmulatorSelectionTest {

    @Test
    fun `a shared extension selects its dedicated emulator without loading the lazy one`() = runTest {
        val spc = StaticEmulator(listOf("spc"))
        val vgmstream = LazyEmulator(listOf("spc", "adx", "hca"))

        val selected = EmulatorSelector.select(listOf(spc, vgmstream), "spc")

        assertSame(spc, selected, "spc must route to the dedicated emulator")
        assertEquals(0, vgmstream.loadCount, "a shared extension must not load vgmstream")
    }

    @Test
    fun `every shared extension keeps its dedicated emulator ahead of the lazy catch-all`() = runTest {
        // The overlap vgmstream declares with the dedicated backends: spc/psf/ssf/dsf/gsf.
        val dedicated = listOf(
            StaticEmulator(listOf("spc")),
            StaticEmulator(listOf("psf", "minipsf", "psf2", "minipsf2")),
            StaticEmulator(listOf("ssf", "minissf", "dsf", "minidsf")),
            StaticEmulator(listOf("gsf", "minigsf")),
        )
        val vgmstream = LazyEmulator(listOf("spc", "psf", "ssf", "dsf", "gsf", "adx"))
        val ordered = dedicated + vgmstream

        for (extension in listOf("spc", "psf", "ssf", "dsf", "gsf")) {
            val selected = EmulatorSelector.select(ordered, extension)

            assertTrue(
                dedicated.any { it === selected },
                "shared extension '$extension' must keep its dedicated emulator",
            )
        }
        assertEquals(0, vgmstream.loadCount, "no shared extension should load the lazy backend")
    }

    @Test
    fun `a lazy-only extension loads the backend once and selects it`() = runTest {
        val spc = StaticEmulator(listOf("spc"))
        val vgmstream = LazyEmulator(listOf("spc", "adx", "hca"))

        val selected = EmulatorSelector.select(listOf(spc, vgmstream), "adx")

        assertSame(vgmstream, selected, "adx is only handled by the lazy backend")
        assertEquals(1, vgmstream.loadCount, "selecting a lazy-only extension must load it exactly once")
    }

    @Test
    fun `repeated selection does not reload the lazy backend`() = runTest {
        val vgmstream = LazyEmulator(listOf("adx", "hca", "brstm"))
        val emulators = listOf(vgmstream)

        assertSame(vgmstream, EmulatorSelector.select(emulators, "adx"))
        assertSame(vgmstream, EmulatorSelector.select(emulators, "hca"))
        assertSame(vgmstream, EmulatorSelector.select(emulators, "brstm"))

        assertEquals(1, vgmstream.loadCount, "the module must survive across selections")
    }

    @Test
    fun `a lazy load failure propagates instead of looking like no emulator`() = runTest {
        val failure = IllegalStateException("chipbox_vgmstream.wasm failed to instantiate")
        val vgmstream = LazyEmulator(listOf("adx"), loadFailure = failure)

        val thrown = assertFailsWith<IllegalStateException> {
            EmulatorSelector.select(listOf(StaticEmulator(listOf("spc")), vgmstream), "adx")
        }

        assertEquals(failure.message, thrown.message)
        assertEquals(1, vgmstream.loadCount)
    }

    /** A backend with a static extension list; uses the default [Emulator.supportsExtension]. */
    private class StaticEmulator(
        override val supportedFileExtensions: List<String>,
    ) : Emulator() {
        override fun loadNativeLib() = Unit
        override fun loadTrackInternal(path: String) = Unit
        override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int) = 0
        override fun teardownInternal() = Unit
        override fun getLastError(): String? = null
        override fun getSampleRateInternal() = SAMPLE_RATE
    }

    /**
     * Mirrors the browser vgmstream backend: its extension list throws until the module has loaded,
     * and [supportsExtension] loads on demand. Counts loads so the test can prove laziness and
     * one-shot loading.
     */
    private class LazyEmulator(
        private val extensions: List<String>,
        private val loadFailure: Throwable? = null,
    ) : Emulator() {
        var loadCount = 0
            private set

        private var loaded = false

        override val supportedFileExtensions: List<String>
            get() {
                check(loaded) { "supportedFileExtensions read before the module loaded" }
                return extensions
            }

        override suspend fun ensureNativeLibReady() {
            if (loaded) return
            loadCount++
            loadFailure?.let { throw it }
            loaded = true
        }

        override suspend fun supportsExtension(extension: String): Boolean {
            ensureNativeLibReady()
            return isFileExtensionSupported(extension)
        }

        override fun loadNativeLib() = Unit
        override fun loadTrackInternal(path: String) = Unit
        override fun generateBufferInternal(buffer: ShortArray, framesPerBuffer: Int) = 0
        override fun teardownInternal() = Unit
        override fun getLastError(): String? = null
        override fun getSampleRateInternal() = SAMPLE_RATE
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
    }
}
