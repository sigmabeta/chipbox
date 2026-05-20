package net.sigmabeta.chipbox.ui.theme.tokens

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle

private val DefaultPlatformTextStyle = PlatformTextStyle(
    includeFontPadding = false,
)

internal actual fun chipboxDefaultTextStyle(): TextStyle =
    TextStyle.Default.copy(platformStyle = DefaultPlatformTextStyle)
