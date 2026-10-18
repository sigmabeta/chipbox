package net.sigmabeta.chipbox.js.navigation

import kotlin.js.jsTypeOf
import kotlinx.browser.window
import net.sigmabeta.chipbox.capabilities.AppCapabilities
import net.sigmabeta.chipbox.common.appui.api.BrowserNavigation
import net.sigmabeta.chipbox.common.appui.api.ShellNavigation
import net.sigmabeta.chipbox.common.appui.api.ShellTab
import net.sigmabeta.chipbox.common.appui.api.ShellTarget
import net.sigmabeta.chipbox.webroutes.ChipboxTab
import net.sigmabeta.chipbox.webroutes.ChipboxTarget
import net.sigmabeta.chipbox.webroutes.RouteUrl
import org.w3c.dom.events.Event

/**
 * Browser history bridge (ADR-0003: "Voyager routes are mirrored into browser URLs with
 * `pushState`"). The pure route<->URL rules live in `:cbox:common:webroutes`; this class owns only
 * the `window.history` / `window.location` side.
 *
 * Direction of truth:
 * - **Shell -> browser:** the shell reports every user navigation (`onTargetChanged`). A forward
 *   navigation pushes a new entry; an in-app pop replaces the current entry (the shell only pops
 *   itself when there is no app entry behind us). A null/duplicate URL is skipped.
 * - **Browser -> shell:** `popstate` decodes the new pathname and drives the shell through
 *   `ShellNavigation`. While it does, `applying` suppresses the shell's report so the browser does
 *   not get a duplicate `pushState` (the history entry already exists).
 * - **Initial load / refresh:** `onShellReady` decodes the current URL once, drives the shell to it,
 *   and canonicalises the entry with `replaceState` (unknown paths become `/`).
 * - **Back (Escape / up arrow / device back):** who owns it. If this entry has an app entry behind
 *   it (`depth > 0`), the host runs `history.back()` and `popstate` applies the target; otherwise
 *   the shell is at the beginning of app history and pops normally, then reports the pop so the
 *   current entry's URL is replaced.
 *
 * `?api=` (the dev server base-URL override read by `RemoteRepository.resolveBaseUrl`) is preserved
 * on every URL the bridge writes, so a refresh keeps pointing at the same API origin. It is not a
 * route argument and `RouteUrl.decode` ignores it.
 */
class BrowserHistoryNavigation(
    private val capabilities: AppCapabilities,
) : BrowserNavigation {

    private var shell: ShellNavigation? = null

    /** How many app entries are behind the current one. 0 means there is no in-app Back target. */
    private var depth: Int = 0

    /** True while a popstate (or the initial deep link) is being applied; suppress write-backs. */
    private var applying: Boolean = false

    private val apiOverride: String? = window.location.search
        .removePrefix("?")
        .split('&')
        .firstOrNull { it.startsWith("api=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }

    init {
        window.addEventListener("popstate", { _: Event -> onPopState() })
    }

    override fun onShellReady(shell: ShellNavigation) {
        this.shell = shell
        depth = readDepth(window.history.state)
        val target = targetForCurrentLocation()
        applying = true
        try {
            shell.navigateTo(target)
        } finally {
            applying = false
        }
        // Canonicalise the entry we loaded on: unknown path -> "/", a deep link -> its own encoded
        // path (so a pasted link with stray query/trailing slash stays stable across refreshes).
        replaceCurrent(target)
    }

    override fun onTargetChanged(target: ShellTarget, push: Boolean) {
        if (applying || shell == null) return
        val url = encode(target) ?: return
        if (push) {
            if (isCurrentLocation(url)) return
            depth += 1
            window.history.pushState(historyState(depth), "", withApiOverride(url))
        } else {
            replaceCurrent(target)
        }
    }

    override fun onBackRequested(): Boolean {
        if (shell == null || depth <= 0) return false
        window.history.back()
        return true
    }

    override fun onShellDisposed() {
        shell = null
    }

    private fun onPopState() {
        val shell = shell ?: return
        depth = readDepth(window.history.state)
        val path = currentPath()
        val target = targetForPath(path)
        applying = true
        try {
            shell.navigateTo(target)
        } finally {
            applying = false
        }
        // A Back/Forward onto an unknown path falls back to the root above; replace the dead URL so
        // the address bar matches what is on screen and a refresh doesn't repeat the fallback.
        if (RouteUrl.needsCanonicalization(path, capabilities)) replaceCurrent(target)
    }

    private fun currentPath(): String = window.location.pathname + window.location.search

    private fun targetForCurrentLocation(): ShellTarget = targetForPath(currentPath())

    private fun targetForPath(path: String): ShellTarget {
        val decoded = RouteUrl.decode(path, capabilities)
        return when (decoded) {
            is ChipboxTarget.Tab -> ShellTarget.Tab(decoded.tab.toShellTab())
            is ChipboxTarget.Deep -> ShellTarget.Deep(decoded.destination)
            // Unknown app path: fall back to the root screen (and canonicalise to "/").
            null -> ShellTarget.Tab(ShellTab.HOME)
        }
    }

    private fun encode(target: ShellTarget): String? = when (target) {
        is ShellTarget.Tab -> RouteUrl.encode(ChipboxTarget.Tab(target.tab.toChipboxTab()))
        is ShellTarget.Deep -> RouteUrl.encode(ChipboxTarget.Deep(target.destination))
    }

    private fun replaceCurrent(target: ShellTarget) {
        val url = encode(target) ?: return
        window.history.replaceState(historyState(depth), "", withApiOverride(url))
    }

    private fun isCurrentLocation(url: String): Boolean {
        val current = window.location.pathname + window.location.search
        return current == withApiOverride(url) || current == url
    }

    private fun withApiOverride(url: String): String {
        val api = apiOverride ?: return url
        if ("api=" in url) return url
        return url + (if ('?' in url) "&" else "?") + "api=$api"
    }
}

private fun ShellTab.toChipboxTab(): ChipboxTab = when (this) {
    ShellTab.HOME -> ChipboxTab.HOME
    ShellTab.LIBRARY -> ChipboxTab.LIBRARY
    ShellTab.SEARCH -> ChipboxTab.SEARCH
}

private fun ChipboxTab.toShellTab(): ShellTab = when (this) {
    ChipboxTab.HOME -> ShellTab.HOME
    ChipboxTab.LIBRARY -> ShellTab.LIBRARY
    ChipboxTab.SEARCH -> ShellTab.SEARCH
}

/**
 * History state entry. `chipbox = true` marks entries this app created, and `depth` is the number
 * of app entries behind this one — enough for `onBackRequested` to know whether `history.back()`
 * stays inside the app (an entry with depth 0 came from a fresh open/refresh).
 */
private fun historyState(depth: Int): dynamic {
    val state: dynamic = js("({})")
    state.chipbox = true
    state.depth = depth
    return state
}

private fun readDepth(state: dynamic): Int {
    if (state == null) return 0
    val isChipbox: dynamic = state.chipbox
    if (isChipbox != true) return 0
    val rawDepth: dynamic = state.depth
    return if (jsTypeOf(rawDepth) == "number") (rawDepth as Double).toInt().coerceAtLeast(0) else 0
}
