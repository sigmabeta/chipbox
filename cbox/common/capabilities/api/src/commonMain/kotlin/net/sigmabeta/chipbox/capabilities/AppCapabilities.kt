package net.sigmabeta.chipbox.capabilities

/**
 * Which app-level surfaces a host exposes. A capability is *policy*, not platform detection: the
 * same shared Compose UI runs on Android, desktop, and the browser, and only the host that builds
 * the DI graph decides what makes sense there.
 *
 * The browser is a client of a self-hosted `chipbox-server` (see ADR-0003). Library
 * administration belongs to whoever runs that server — the browser can neither add a folder nor
 * rescan one — so the web graph binds [canManageLibrary] and [showDebugSection] to false while
 * leaving [showErrorLog] true (the in-memory log is genuinely browser-local).
 *
 * Defaults are the full desktop/Android surface, so a graph that does not bind this type keeps
 * today's behaviour. Hosts override it explicitly in their DI modules instead of leaning on
 * `expect`/`actual` or on `AppInfo.isDebug` (a release desktop build may still want its debug
 * section, and a debug web build must still hide library administration).
 *
 * @property canManageLibrary whether Settings and Home may expose library-folder management
 *   (Manage Library, Folder Picker, Rescan Status, Clear Library). When false, the browser's
 *   library is server-owned: files are added on the server and picked up on restart.
 * @property showDebugSection whether Settings may render the debug section (source switches,
 *   snapshot import, Playback Status, Crash Log, Component Library). This gates the section
 *   independently of the persisted five-tap toggle.
 * @property showErrorLog whether the Error Log is reachable outside the debug section. It is
 *   rendered as a Diagnostics row when [showDebugSection] is false; when debug is available the
 *   existing debug-section row is the entry point, so desktop/Android output is unchanged.
 */
data class AppCapabilities(
    val canManageLibrary: Boolean = true,
    val showDebugSection: Boolean = true,
    val showErrorLog: Boolean = true,
) {
    companion object {
        /** The full desktop/Android surface; also the default graph binding. */
        val Default: AppCapabilities = AppCapabilities()
    }
}
