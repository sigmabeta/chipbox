package net.sigmabeta.chipbox.ui.theme.tokens

import androidx.compose.ui.text.TextStyle

// JVM/desktop has no `includeFontPadding` knob (the legacy Android font-padding default
// doesn't exist on Skia/AWT), so the base style is just `TextStyle.Default`.
internal actual fun chipboxDefaultTextStyle(): TextStyle = TextStyle.Default
