package net.sigmabeta.chipbox.server.http

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The SPA fallback must not answer for a missing asset.
 *
 * A missing `/wasm/` loader used to receive `index.html` with HTTP 200: the browser parsed HTML as
 * JavaScript (a console SyntaxError), the Emscripten factory global never existed, and playback
 * stalled with no actionable error. Missing assets are now a real 404 so the injected `<script>`
 * fires its `error` listener and the loader can surface "the bundle may lack WASM".
 *
 * The app-route fallback still has to work — deep links and refresh depend on it.
 */
class StaticRoutesTest {

    @Test
    fun `a missing wasm module is a real 404, not the SPA HTML`() = testApplication {
        application { routing { staticRoutes() } }

        val response = client.get("/wasm/chipbox_missing.js")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertFalse(
            response.bodyAsText().contains("chipbox-test-index"),
            "the SPA index must never be served for a missing WASM file",
        )
    }

    @Test
    fun `a missing asset at the bundle root is a real 404`() = testApplication {
        application { routing { staticRoutes() } }

        // Use names that are absent even when a real bundle is on the test classpath: the CI web job
        // (scripts/ci/web.sh) runs `installDist` before `:apps:server:test`, and `installProductionJsBundle`
        // writes the production bundle into build/generated/static, which is a main resources root. A
        // fixture like `/chipbox.js` is therefore present in that ordering, so it no longer exercises
        // the missing-asset fallback. The policy under test is "asset-shaped path -> 404", not the
        // existence of a specific bundle file.
        assertEquals(HttpStatusCode.NotFound, client.get("/definitely-missing-asset.js").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/definitely-missing-favicon.png").status)
    }

    @Test
    fun `a missing file below wasm is a 404 even without an extension`() = testApplication {
        application { routing { staticRoutes() } }

        assertEquals(HttpStatusCode.NotFound, client.get("/wasm/chipbox_missing").status)
    }

    @Test
    fun `an app route still falls back to the SPA index`() = testApplication {
        application { routing { staticRoutes() } }

        val response = client.get("/library/game/42")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.bodyAsText().contains("<html"),
            "deep links and refresh depend on the SPA fallback",
        )
    }
}
