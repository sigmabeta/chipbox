package net.sigmabeta.chipbox.ui.theme.api

import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.chipbox.ui.fonts.real.toFontFamily
import net.sigmabeta.chipbox.ui.theme.api.tokens.ChipboxFontDefaults

/**
 * High-level Chipbox theme entry point — the `ChipboxFont`-typed convenience over [ChipboxTheme]
 * (which takes raw `FontFamily`). Converts the picked `ChipboxFont`s to `FontFamily`s via the
 * multiplatform `ChipboxFont.toFontFamily()` (loading each Compose Multiplatform font resource)
 * and folds their per-font `scaleFactor`s into the scales. Multiplatform — used by both the shared
 * `ChipboxAppUi` (Android + desktop) and Compose previews. [darkTheme] is `null` to follow the
 * system setting, or `true`/`false` to force a scheme.
 */
@Composable
fun AppTheme(
    brand: ChipboxFont = ChipboxFontDefaults.Brand,
    plain: ChipboxFont = ChipboxFontDefaults.Plain,
    fontScale: Float = 1.0f,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    ChipboxTheme(
        brand = brand.toFontFamily(),
        plain = plain.toFontFamily(),
        brandScale = brand.scaleFactor * fontScale,
        plainScale = plain.scaleFactor * fontScale,
        darkTheme = darkTheme,
        content = content,
    )
}
