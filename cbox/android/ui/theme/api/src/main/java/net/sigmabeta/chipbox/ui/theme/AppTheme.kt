package net.sigmabeta.chipbox.ui.theme

import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypefaceTokens
import net.sigmabeta.sage.ui.themes.SageMaterial
import net.sigmabeta.sage.ui.themes.SageMaterialMenu

@Composable
fun AppTheme(
    brand: ChipboxFont = ChipboxTypefaceTokens.Brand,
    plain: ChipboxFont = ChipboxTypefaceTokens.Plain,
    fontScale: Float = 1.0f,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    SageMaterial(
        lightColors = ChipboxLight,
        darkColors = ChipboxDark,
        typography = buildChipboxTypography(brand, plain, fontScale),
        forceDark = forceDark,
        content = content,
    )
}

@Composable
fun AppThemeMenu(
    brand: ChipboxFont = ChipboxTypefaceTokens.Brand,
    plain: ChipboxFont = ChipboxTypefaceTokens.Plain,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    SageMaterialMenu(
        menuColors = ChipboxMenu,
        typography = buildChipboxTypography(brand, plain, fontScale),
        content = content,
    )
}
