package net.sigmabeta.chipbox.ui.theme.api

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * Multiplatform Chipbox theme. Wraps Material3 with the Chipbox [ChipboxLight] / [ChipboxDark]
 * color schemes (light/dark picked from [isSystemInDarkTheme], overridable via [forceDark]) and
 * a Chipbox-shaped [androidx.compose.material3.Typography] built from caller-supplied font
 * families. Defaults to [FontFamily.Default] so the JVM/desktop entry can use it directly; the
 * Android `AppTheme()` wraps this one with ChipboxFont-derived families.
 *
 * Inlines the (Android-only) `SageMaterial` helper's body — `isSystemInDarkTheme()` itself is
 * multiplatform (it lives in `androidx.compose.foundation`), so once SageMaterial's scheme pick
 * is replicated here, no Android-only theme dep is needed on the JVM side.
 */
@Composable
fun ChipboxTheme(
    brand: FontFamily = FontFamily.Default,
    plain: FontFamily = FontFamily.Default,
    brandScale: Float = 1.0f,
    plainScale: Float = 1.0f,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (!isSystemInDarkTheme() && !forceDark) ChipboxLight else ChipboxDark
    MaterialTheme(
        colorScheme = colors,
        typography = buildChipboxTypography(brand, plain, brandScale, plainScale),
        content = content,
    )
}

/**
 * Variant that pins the [ChipboxMenu] color scheme (used by Android menu/overflow surfaces).
 * Doesn't honor system dark mode — the menu palette is intentionally fixed.
 */
@Composable
fun ChipboxThemeMenu(
    brand: FontFamily = FontFamily.Default,
    plain: FontFamily = FontFamily.Default,
    brandScale: Float = 1.0f,
    plainScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ChipboxMenu,
        typography = buildChipboxTypography(brand, plain, brandScale, plainScale),
        content = content,
    )
}
