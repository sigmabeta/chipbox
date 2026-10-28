package net.sigmabeta.chipbox.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deep-link asset loading depends on the shipped `index.html`, not on a test fixture: the server
 * returns that same file for `/` and for every app path (`/game/42`, `/artist/9/tracks`, …).
 *
 * Without an absolute base, the relative `favicon.png`, `splash.png`, and `chipbox.js` URLs resolve
 * under the current path (`/game/chipbox.js`) and the page loads blank on refresh. The test reads
 * the real source file (path supplied by the Gradle test task) so the fixture used by
 * [UnknownApiRouteTest] cannot hide a regression.
 */
class WebIndexHtmlTest {

    @Test
    fun `the web index pins every asset to the site root`() {
        val path = System.getProperty("chipbox.jsIndexHtml")
            ?: error("chipbox.jsIndexHtml test system property is not set")
        val html = File(path).readText()

        assertTrue(
            html.contains("<base href=\"/\">"),
            "index.html must declare <base href=\"/\"> so deep links resolve assets at the root",
        )
        assertTrue(html.contains("href=\"favicon.png\""), "favicon stays relative to the base")
        assertTrue(html.contains("src=\"splash.png\""), "splash stays relative to the base")
        assertTrue(html.contains("src=\"chipbox.js\""), "the bundle stays relative to the base")
    }
}
