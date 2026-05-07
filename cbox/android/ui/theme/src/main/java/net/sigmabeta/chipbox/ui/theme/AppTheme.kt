package net.sigmabeta.chipbox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypefaceTokens
import net.sigmabeta.sage.ui.themes.SageMaterial
import net.sigmabeta.sage.ui.themes.SageMaterialMenu

@Composable
fun AppTheme(
    brandFont: FontFamily = ChipboxTypefaceTokens.Brand,
    plainFont: FontFamily = ChipboxTypefaceTokens.Plain,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    SageMaterial(
        lightColors = ChipboxLight,
        darkColors = ChipboxDark,
        typography = buildChipboxTypography(brandFont, plainFont),
        forceDark = forceDark,
        content = content,
    )
}

@Composable
fun AppThemeMenu(
    brandFont: FontFamily = ChipboxTypefaceTokens.Brand,
    plainFont: FontFamily = ChipboxTypefaceTokens.Plain,
    content: @Composable () -> Unit,
) {
    SageMaterialMenu(
        menuColors = ChipboxMenu,
        typography = buildChipboxTypography(brandFont, plainFont),
        content = content,
    )
}
