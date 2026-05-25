package net.sigmabeta.chipbox.ui.theme.api.tokens

import androidx.compose.ui.text.TextStyle

// Like JVM/desktop, the JS (Skiko) backend has no `includeFontPadding` knob (that's a
// legacy Android-only default), so the base style is just `TextStyle.Default`.
internal actual fun chipboxDefaultTextStyle(): TextStyle = TextStyle.Default
