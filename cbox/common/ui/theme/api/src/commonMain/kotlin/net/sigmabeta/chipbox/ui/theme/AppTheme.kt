package net.sigmabeta.chipbox.ui.theme.api

import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.chipbox.ui.fonts.real.toFontFamily
import net.sigmabeta.chipbox.ui.theme.api.tokens.ChipboxFontDefaults

/**
 * High-level Chipbox theme entry point — the `ChipboxFont`-typed convenience over [ChipboxTheme]
 * (which takes raw `FontFamily`). Converts the picked `ChipboxFont`s to `FontFamily`s via the
 * multiplatform `ChipboxFont.toFontFamily()` (loading each Compose Multiplatform font resource).
 * Multiplatform — used by both the shared `ChipboxAppUi` (Android + desktop) and Compose
 * previews. [darkTheme] is `null` to follow the system setting, or `true`/`false` to force a
 * scheme. [swapPrimaryAndSecondary] exchanges the primary/secondary roles to mark debug builds —
 * see [ChipboxTheme].
 */
@Composable
fun AppTheme(
    brand: ChipboxFont = ChipboxFontDefaults.Brand,
    plain: ChipboxFont = ChipboxFontDefaults.Plain,
    fontScale: Float = 1.0f,
    darkTheme: Boolean? = null,
    swapPrimaryAndSecondary: Boolean = false,
    content: @Composable () -> Unit,
) {
    ChipboxTheme(
        brand = brand.toFontFamily(),
        plain = plain.toFontFamily(),
        scale = fontScale,
        darkTheme = darkTheme,
        swapPrimaryAndSecondary = swapPrimaryAndSecondary,
        content = content,
    )
}
