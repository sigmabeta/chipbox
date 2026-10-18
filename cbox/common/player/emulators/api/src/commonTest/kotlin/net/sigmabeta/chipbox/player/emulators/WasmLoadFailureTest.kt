package net.sigmabeta.chipbox.player.emulators

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The browser WASM loader has several failure modes that used to be indistinguishable — and two
 * of them (a missing factory global after the SPA fallback served HTML, and a rejected module
 * promise) were silent or uncaught. Each maps to an actionable message that names the module URL
 * and points at the bundle, so the user sees *why* the track can't play instead of a stall.
 */
class WasmLoadFailureTest {

    @Test
    fun `every failure names the module, the URL, and the bundle remedy`() {
        val kinds = WasmLoadFailureKind.entries
        val messages = kinds.map { kind ->
            WasmLoadFailure(
                moduleName = "gme",
                loaderUrl = "/wasm/chipbox_gme.js",
                kind = kind,
                detail = "some detail",
            ).message
        }

        messages.forEachIndexed { index, message ->
            val kind = kinds[index]
            assertTrue(
                message.contains("gme"),
                "$kind should name the module: $message",
            )
            assertTrue(
                message.contains("/wasm/chipbox_gme.js"),
                "$kind should name the loader URL: $message",
            )
            assertTrue(
                message.contains("bundle may lack WASM"),
                "$kind should explain the likely packaging cause: $message",
            )
            assertTrue(
                message.contains("some detail"),
                "$kind should keep the underlying detail: $message",
            )
        }
    }

    @Test
    fun `failure kinds explain their distinct causes`() {
        fun message(kind: WasmLoadFailureKind) =
            WasmLoadFailure("psf", "/wasm/chipbox_psf.js", kind).message

        assertTrue(message(WasmLoadFailureKind.TIMEOUT).contains("timed out"))
        assertTrue(message(WasmLoadFailureKind.SCRIPT_ERROR).contains("script failed to load"))
        assertTrue(message(WasmLoadFailureKind.FACTORY_MISSING).contains("factory function is missing"))
        assertTrue(message(WasmLoadFailureKind.FACTORY_REJECTED).contains("factory rejected"))
        assertTrue(message(WasmLoadFailureKind.MODULE_REJECTED).contains("module failed to initialize"))
    }

    @Test
    fun `the detail is omitted when there is none`() {
        val message = WasmLoadFailure(
            moduleName = "vgm",
            loaderUrl = "/wasm/chipbox_vgm.js",
            kind = WasmLoadFailureKind.TIMEOUT,
        ).message

        assertTrue("Details:" !in message)
        assertTrue(message.endsWith("."), "a message without details still ends cleanly: $message")
    }

    @Test
    fun `classify picks the kind from the loader observation`() {
        assertEquals(
            WasmLoadFailureKind.FACTORY_MISSING,
            WasmLoadFailure.failureKind(factoryIsFunction = false, scriptError = false),
        )
        assertEquals(
            WasmLoadFailureKind.SCRIPT_ERROR,
            WasmLoadFailure.failureKind(factoryIsFunction = false, scriptError = true),
        )
        assertEquals(
            null,
            WasmLoadFailure.failureKind(factoryIsFunction = true, scriptError = false),
            "a loaded script with a callable factory is not a failure",
        )
    }
}
