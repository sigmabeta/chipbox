package net.sigmabeta.chipbox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypefaceTokens
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypographyTokens

fun buildChipboxTypography(
    brandFont: FontFamily = ChipboxTypefaceTokens.Brand,
    plainFont: FontFamily = ChipboxTypefaceTokens.Plain,
): Typography = Typography(
    displayLarge   = ChipboxTypographyTokens.DisplayLarge.copy(fontFamily = brandFont),
    displayMedium  = ChipboxTypographyTokens.DisplayMedium.copy(fontFamily = brandFont),
    displaySmall   = ChipboxTypographyTokens.DisplaySmall.copy(fontFamily = brandFont),
    headlineLarge  = ChipboxTypographyTokens.HeadlineLarge.copy(fontFamily = brandFont),
    headlineMedium = ChipboxTypographyTokens.HeadlineMedium.copy(fontFamily = brandFont),
    headlineSmall  = ChipboxTypographyTokens.HeadlineSmall.copy(fontFamily = brandFont),
    titleLarge     = ChipboxTypographyTokens.TitleLarge.copy(fontFamily = brandFont),
    titleMedium    = ChipboxTypographyTokens.TitleMedium.copy(fontFamily = plainFont),
    titleSmall     = ChipboxTypographyTokens.TitleSmall.copy(fontFamily = plainFont),
    bodyLarge      = ChipboxTypographyTokens.BodyLarge.copy(fontFamily = plainFont),
    bodyMedium     = ChipboxTypographyTokens.BodyMedium.copy(fontFamily = plainFont),
    bodySmall      = ChipboxTypographyTokens.BodySmall.copy(fontFamily = plainFont),
    labelLarge     = ChipboxTypographyTokens.LabelLarge.copy(fontFamily = plainFont),
    labelMedium    = ChipboxTypographyTokens.LabelMedium.copy(fontFamily = plainFont),
    labelSmall     = ChipboxTypographyTokens.LabelSmall.copy(fontFamily = plainFont),
)
