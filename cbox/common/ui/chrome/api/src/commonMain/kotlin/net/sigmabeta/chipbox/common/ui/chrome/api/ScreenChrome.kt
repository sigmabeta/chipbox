package net.sigmabeta.chipbox.common.ui.chrome.api

data class ScreenChrome(
    val showTopBar: Boolean = true,
    val showNavBar: Boolean = true,
    val showPlayerStatus: Boolean = true,
) {
    companion object {
        val Default = ScreenChrome()
    }
}
