package net.sigmabeta.chipbox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypefaceTokens
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypeScaleTokens
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypographyTokens

fun buildChipboxTypography(
    brandFont: FontFamily = ChipboxTypefaceTokens.Brand,
    plainFont: FontFamily = ChipboxTypefaceTokens.Plain,
    fontScale: Float = 1.0f,
): Typography = Typography(
    displayLarge   = ChipboxTypographyTokens.DisplayLarge.copy(fontFamily = brandFont, fontSize = ChipboxTypeScaleTokens.DisplayLargeSize * fontScale, lineHeight = ChipboxTypeScaleTokens.DisplayLargeLineHeight * fontScale),
    displayMedium  = ChipboxTypographyTokens.DisplayMedium.copy(fontFamily = brandFont, fontSize = ChipboxTypeScaleTokens.DisplayMediumSize * fontScale, lineHeight = ChipboxTypeScaleTokens.DisplayMediumLineHeight * fontScale),
    displaySmall   = ChipboxTypographyTokens.DisplaySmall.copy(fontFamily = brandFont, fontSize = ChipboxTypeScaleTokens.DisplaySmallSize * fontScale, lineHeight = ChipboxTypeScaleTokens.DisplaySmallLineHeight * fontScale),
    headlineLarge  = ChipboxTypographyTokens.HeadlineLarge.copy(fontFamily = brandFont, fontSize = ChipboxTypeScaleTokens.HeadlineLargeSize * fontScale, lineHeight = ChipboxTypeScaleTokens.HeadlineLargeLineHeight * fontScale),
    headlineMedium = ChipboxTypographyTokens.HeadlineMedium.copy(fontFamily = brandFont, fontSize = ChipboxTypeScaleTokens.HeadlineMediumSize * fontScale, lineHeight = ChipboxTypeScaleTokens.HeadlineMediumLineHeight * fontScale),
    headlineSmall  = ChipboxTypographyTokens.HeadlineSmall.copy(fontFamily = brandFont, fontSize = ChipboxTypeScaleTokens.HeadlineSmallSize * fontScale, lineHeight = ChipboxTypeScaleTokens.HeadlineSmallLineHeight * fontScale),
    titleLarge     = ChipboxTypographyTokens.TitleLarge.copy(fontFamily = brandFont, fontSize = ChipboxTypeScaleTokens.TitleLargeSize * fontScale, lineHeight = ChipboxTypeScaleTokens.TitleLargeLineHeight * fontScale),
    titleMedium    = ChipboxTypographyTokens.TitleMedium.copy(fontFamily = plainFont, fontSize = ChipboxTypeScaleTokens.TitleMediumSize * fontScale, lineHeight = ChipboxTypeScaleTokens.TitleMediumLineHeight * fontScale),
    titleSmall     = ChipboxTypographyTokens.TitleSmall.copy(fontFamily = plainFont, fontSize = ChipboxTypeScaleTokens.TitleSmallSize * fontScale, lineHeight = ChipboxTypeScaleTokens.TitleSmallLineHeight * fontScale),
    bodyLarge      = ChipboxTypographyTokens.BodyLarge.copy(fontFamily = plainFont, fontSize = ChipboxTypeScaleTokens.BodyLargeSize * fontScale, lineHeight = ChipboxTypeScaleTokens.BodyLargeLineHeight * fontScale),
    bodyMedium     = ChipboxTypographyTokens.BodyMedium.copy(fontFamily = plainFont, fontSize = ChipboxTypeScaleTokens.BodyMediumSize * fontScale, lineHeight = ChipboxTypeScaleTokens.BodyMediumLineHeight * fontScale),
    bodySmall      = ChipboxTypographyTokens.BodySmall.copy(fontFamily = plainFont, fontSize = ChipboxTypeScaleTokens.BodySmallSize * fontScale, lineHeight = ChipboxTypeScaleTokens.BodySmallLineHeight * fontScale),
    labelLarge     = ChipboxTypographyTokens.LabelLarge.copy(fontFamily = plainFont, fontSize = ChipboxTypeScaleTokens.LabelLargeSize * fontScale, lineHeight = ChipboxTypeScaleTokens.LabelLargeLineHeight * fontScale),
    labelMedium    = ChipboxTypographyTokens.LabelMedium.copy(fontFamily = plainFont, fontSize = ChipboxTypeScaleTokens.LabelMediumSize * fontScale, lineHeight = ChipboxTypeScaleTokens.LabelMediumLineHeight * fontScale),
    labelSmall     = ChipboxTypographyTokens.LabelSmall.copy(fontFamily = plainFont, fontSize = ChipboxTypeScaleTokens.LabelSmallSize * fontScale, lineHeight = ChipboxTypeScaleTokens.LabelSmallLineHeight * fontScale),
)
