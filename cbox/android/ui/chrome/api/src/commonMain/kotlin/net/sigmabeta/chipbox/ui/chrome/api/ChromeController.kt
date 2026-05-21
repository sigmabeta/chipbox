package net.sigmabeta.chipbox.ui.chrome.api

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class ChromeController {
    var state: ScreenChrome by mutableStateOf(ScreenChrome.Default)
        private set

    fun set(chrome: ScreenChrome) {
        state = chrome
    }
}

val LocalChromeController = staticCompositionLocalOf<ChromeController> {
    error("LocalChromeController not provided")
}
