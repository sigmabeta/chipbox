package net.sigmabeta.chipbox.js.inert

import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.chipbox.scanner.Scanner

/**
 * The browser never scans: `chipbox-server` scans `CHIPBOX_LIBRARY_DIR` at startup. This inert
 * [Scanner] exists only so the shared view models that still take a [Scanner] (Home, Settings,
 * Manage Library) can be constructed inside the Metro graph. Library administration is disabled on
 * web ([net.sigmabeta.chipbox.capabilities.AppCapabilities.canManageLibrary] = false), so no UI
 * path starts a scan; if one ever did, this would stay idle rather than scan browser memory.
 */
class NoopScanner : Scanner() {
    override suspend fun CoroutineScope.scan() = Unit
}
