package net.sigmabeta.chipbox.ui.chrome

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import net.sigmabeta.sage.components.TitleBarModel

@Stable
class TitleBarController {
    var state: TitleBarModel by mutableStateOf(TitleBarModel(title = null, shouldShowBack = false))
        private set

    fun set(model: TitleBarModel) {
        state = model
    }
}

val LocalTitleBarController = staticCompositionLocalOf<TitleBarController> {
    error("LocalTitleBarController not provided")
}
