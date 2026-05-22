package net.sigmabeta.chipbox.ui.theme.api

import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.chipbox.ui.theme.api.tokens.ChipboxFontDefaults

/**
 * Android-side wrapper preserving the historic `AppTheme(brand: ChipboxFont, ...)` signature
 * (so existing Compose preview / feature call sites build unchanged) on top of the shared
 * [ChipboxTheme]. Converts the picked `ChipboxFont`s to `FontFamily`s here (via the multiplatform
 * `ChipboxFont.toFontFamily()`, which loads each Compose Multiplatform font resource) and folds
 * their per-font `scaleFactor`s into the brand/plain scales passed through to the multiplatform
 * theme. The earlier `SageMaterial` wrapping is gone — its `isSystemInDarkTheme()` + scheme
 * pick logic lives inside [ChipboxTheme] now.
 */
@Composable
fun AppTheme(
    brand: ChipboxFont = ChipboxFontDefaults.Brand,
    plain: ChipboxFont = ChipboxFontDefaults.Plain,
    fontScale: Float = 1.0f,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    ChipboxTheme(
        brand = brand.toFontFamily(),
        plain = plain.toFontFamily(),
        brandScale = brand.scaleFactor * fontScale,
        plainScale = plain.scaleFactor * fontScale,
        // Previews force a specific scheme; null lets the runtime follow the system setting.
        darkTheme = if (forceDark) true else null,
        content = content,
    )
}
