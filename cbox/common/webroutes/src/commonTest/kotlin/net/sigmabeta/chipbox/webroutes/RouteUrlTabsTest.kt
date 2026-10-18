package net.sigmabeta.chipbox.webroutes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Top-level tabs are the URL roots. A tab path is a tab, not a pushed screen; the shell does not
 * keep a Home/Library/Search screen on its deep stack.
 */
class RouteUrlTabsTest {

    @Test
    fun `tab roots encode to their documented paths`() {
        assertEquals("/", RouteUrl.encode(ChipboxTarget.Tab(ChipboxTab.HOME)))
        assertEquals("/library", RouteUrl.encode(ChipboxTarget.Tab(ChipboxTab.LIBRARY)))
        assertEquals("/search", RouteUrl.encode(ChipboxTarget.Tab(ChipboxTab.SEARCH)))
    }

    @Test
    fun `tab paths decode to the matching tab`() {
        assertEquals(ChipboxTarget.Tab(ChipboxTab.HOME), RouteUrl.decode("/"))
        assertEquals(ChipboxTarget.Tab(ChipboxTab.LIBRARY), RouteUrl.decode("/library"))
        assertEquals(ChipboxTarget.Tab(ChipboxTab.SEARCH), RouteUrl.decode("/search"))
    }

    @Test
    fun `tabs are canonicalised`() {
        assertEquals(ChipboxTarget.Tab(ChipboxTab.LIBRARY), RouteUrl.decode("/library/"))
        assertEquals(ChipboxTarget.Tab(ChipboxTab.LIBRARY), RouteUrl.decode("/library?api=http://host:8080"))
    }

    @Test
    fun `unknown paths have no target`() {
        assertNull(RouteUrl.decode("/not-a-chipbox-route"))
        assertNull(RouteUrl.decode("/game"))
        assertNull(RouteUrl.decode(""))
    }
}
