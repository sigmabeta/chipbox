package net.sigmabeta.chipbox.common.appui.api

import net.sigmabeta.chipbox.capabilities.AppCapabilities
import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibrary
import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibraryMode
import net.sigmabeta.chipbox.features.componentlibrary.LibraryMode
import net.sigmabeta.chipbox.features.crashlog.CrashLog
import net.sigmabeta.chipbox.features.errorlog.ErrorLog
import net.sigmabeta.chipbox.features.folderpicker.FolderPicker
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.managelibrary.ManageLibrary
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatus
import net.sigmabeta.chipbox.features.rescanstatus.RescanStatus
import net.sigmabeta.chipbox.features.settings.Settings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shell is the last line of defence: even a stale row, a synthetic event, or a future deep
 * link must not open a surface the host does not expose.
 */
class AppCapabilitiesPolicyTest {

    @Test
    fun `default capabilities keep every destination reachable`() {
        val capabilities = AppCapabilities()

        adminDestinations.forEach { assertTrue(capabilities.allowsNavigation(it), "$it should be allowed") }
        debugDestinations.forEach { assertTrue(capabilities.allowsNavigation(it), "$it should be allowed") }
        assertTrue(capabilities.allowsNavigation(ErrorLog))
        assertTrue(capabilities.allowsNavigation(Settings))
        assertTrue(capabilities.allowsNavigation(GameDetail(42L)))
    }

    @Test
    fun `server-managed library blocks folder and scan destinations`() {
        val capabilities = AppCapabilities(canManageLibrary = false)

        adminDestinations.forEach { assertFalse(capabilities.allowsNavigation(it), "$it should be blocked") }
        assertTrue(capabilities.allowsNavigation(ErrorLog))
    }

    @Test
    fun `hidden debug section blocks debug-only destinations but keeps the error log`() {
        val capabilities = AppCapabilities(showDebugSection = false, showErrorLog = true)

        debugDestinations.forEach { assertFalse(capabilities.allowsNavigation(it), "$it should be blocked") }
        assertTrue(capabilities.allowsNavigation(ErrorLog))
        assertTrue(capabilities.allowsNavigation(Settings))
        assertTrue(capabilities.allowsNavigation(GameDetail(42L)))
    }

    @Test
    fun `withheld error log blocks its route`() {
        val capabilities = AppCapabilities(showDebugSection = false, showErrorLog = false)

        assertFalse(capabilities.allowsNavigation(ErrorLog))
    }

    private companion object {
        val adminDestinations = listOf(ManageLibrary, FolderPicker, RescanStatus)
        val debugDestinations = listOf(
            PlaybackStatus,
            CrashLog,
            ComponentLibrary,
            ComponentLibraryMode(LibraryMode.LIST),
        )
    }
}
