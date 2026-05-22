package net.sigmabeta.chipbox.ui.theme.api

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * Multiplatform Chipbox theme. Wraps Material3 with the Chipbox [net.sigmabeta.chipbox.ui.theme.ChipboxLight] / [net.sigmabeta.chipbox.ui.theme.ChipboxDark]
 * color schemes and a Chipbox-shaped [androidx.compose.material3.Typography] built from
 * caller-supplied font families. Defaults to [FontFamily.Default] so the JVM/desktop entry can
 * use it directly; the Android `AppTheme()` wraps this one with ChipboxFont-derived families.
 *
 * [darkTheme] picks the scheme: `true` forces dark, `false` forces light, and `null` (the
 * default) follows the OS via [isSystemInDarkTheme]. The Settings "Theme" picker drives this
 * through `ChipboxAppUi`.
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
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val isDark = darkTheme ?: isSystemInDarkTheme()
    val colors = if (isDark) ChipboxDark else ChipboxLight
    MaterialTheme(
        colorScheme = colors,
        typography = buildChipboxTypography(brand, plain, brandScale, plainScale),
        content = content,
    )
}

