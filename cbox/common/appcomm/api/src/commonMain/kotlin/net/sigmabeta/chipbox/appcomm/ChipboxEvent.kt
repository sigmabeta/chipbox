package net.sigmabeta.chipbox.appcomm

sealed class ChipboxEvent {
    data class NavigateTo(val destination: Any) : ChipboxEvent()
    data object NavigateBack : ChipboxEvent()
    data class OpenUrl(val url: String) : ChipboxEvent()
    data class ShowSnackbar(
        val message: String,
        val withDismissAction: Boolean = true,
    ) : ChipboxEvent()
    data object PickFolder : ChipboxEvent()
    data class CopyToClipboard(
        val label: String,
        val text: String,
    ) : ChipboxEvent()

    /**
     * Ask the host to show or hide the bottom mini-player. A request, not a command — the
     * collector decides whether to honour it (e.g. a screen that already pinned the
     * mini-player in a particular state can ignore this).
     */
    data class RequestMiniPlayerVisibility(val visible: Boolean) : ChipboxEvent()

    /**
     * Ask the host to show or hide the app-level top bar. Same semantics as
     * [RequestMiniPlayerVisibility]: a request the collector decides whether to honour.
     * Screens that want to embed their own header (Search's in-screen [SearchBar],
     * NowPlaying's swipe-down chevron) emit `visible = false` on entry; on screen change
     * the per-screen scaffold resets chrome to default, so screens don't have to emit a
     * `true` on exit.
     */
    data class RequestTopBarVisibility(val visible: Boolean) : ChipboxEvent()
}
