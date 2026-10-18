package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.staticCompositionLocalOf

/** The three top-level tabs of the shared shell. */
enum class ShellTab { HOME, LIBRARY, SEARCH }

/**
 * A location the shell can be driven to from outside its own event sinks — today only by the
 * browser's `popstate`/deep-link handling. [Tab] selects a tab (clearing the outgoing tab's deep
 * stack, exactly like tapping it); [Deep] pushes or pops a feature route in the active tab.
 */
sealed interface ShellTarget {
    data class Tab(val tab: ShellTab) : ShellTarget
    data class Deep(val destination: Any) : ShellTarget
}

/**
 * The shell's side of [BrowserNavigation]. The host holds this after `onShellReady` and calls it
 * when the browser changes the URL; `navigateTo` is the only way in, so the shell decides whether a
 * target is a tab switch, a pop within the active stack, or a push.
 */
interface ShellNavigation {
    fun navigateTo(target: ShellTarget)
}

/**
 * Web-only browser-history seam: the shell reports its navigation to the host, and the host drives
 * the shell when the browser's Back/Forward buttons change the URL. Desktop and Android pass null
 * (the default) and are unchanged.
 *
 * Contract:
 * - [onShellReady] fires once, after the tab and deep navigators exist. The host applies the initial
 *   URL (deep link / refresh) and canonicalises it; it must not treat this call as user navigation.
 * - [onTargetChanged] reports the shell's resulting location. `push = true` is a forward navigation
 *   (new history entry); `push = false` means an in-app pop already happened and the host should
 *   replace the current entry. Hosts ignore both while applying a `popstate`.
 * - [onBackRequested] asks the host to route an in-app Back (Escape / up arrow / device back) through
 *   browser history. Returning true means the host owns it: it will call `history.back()` and apply
 *   the resulting `popstate`. Returning false means "no app history behind this entry"; the shell
 *   pops normally.
 * - [onShellDisposed] lets the host stop driving a torn-down shell (host teardown/tests).
 */
interface BrowserNavigation {
    fun onShellReady(shell: ShellNavigation)
    fun onTargetChanged(target: ShellTarget, push: Boolean)
    fun onBackRequested(): Boolean
    fun onShellDisposed() {}
}

internal val LocalBrowserNavigation = staticCompositionLocalOf<BrowserNavigation?> { null }
