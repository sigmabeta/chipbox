package net.sigmabeta.chipbox.ui.theme

import androidx.compose.material3.Typography
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypefaceTokens
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypeScaleTokens
import net.sigmabeta.chipbox.ui.theme.tokens.ChipboxTypographyTokens
import net.sigmabeta.chipbox.ui.theme.tokens.toFontFamily

fun buildChipboxTypography(
    brand: ChipboxFont = ChipboxTypefaceTokens.Brand,
    plain: ChipboxFont = ChipboxTypefaceTokens.Plain,
    fontScale: Float = 1.0f,
): Typography {
    val brandFamily = brand.toFontFamily()
    val plainFamily = plain.toFontFamily()
    val brandScale = brand.scaleFactor * fontScale
    val plainScale = plain.scaleFactor * fontScale

    return Typography(
        displayLarge = ChipboxTypographyTokens.DisplayLarge.copy(
            fontFamily = brandFamily,
            fontSize = ChipboxTypeScaleTokens.DisplayLargeSize * brandScale,
            lineHeight = ChipboxTypeScaleTokens.DisplayLargeLineHeight * brandScale,
        ),
        displayMedium = ChipboxTypographyTokens.DisplayMedium.copy(
            fontFamily = brandFamily,
            fontSize = ChipboxTypeScaleTokens.DisplayMediumSize * brandScale,
            lineHeight = ChipboxTypeScaleTokens.DisplayMediumLineHeight * brandScale,
        ),
        displaySmall = ChipboxTypographyTokens.DisplaySmall.copy(
            fontFamily = brandFamily,
            fontSize = ChipboxTypeScaleTokens.DisplaySmallSize * brandScale,
            lineHeight = ChipboxTypeScaleTokens.DisplaySmallLineHeight * brandScale,
        ),
        headlineLarge = ChipboxTypographyTokens.HeadlineLarge.copy(
            fontFamily = brandFamily,
            fontSize = ChipboxTypeScaleTokens.HeadlineLargeSize * brandScale,
            lineHeight = ChipboxTypeScaleTokens.HeadlineLargeLineHeight * brandScale,
        ),
        headlineMedium = ChipboxTypographyTokens.HeadlineMedium.copy(
            fontFamily = brandFamily,
            fontSize = ChipboxTypeScaleTokens.HeadlineMediumSize * brandScale,
            lineHeight = ChipboxTypeScaleTokens.HeadlineMediumLineHeight * brandScale,
        ),
        headlineSmall = ChipboxTypographyTokens.HeadlineSmall.copy(
            fontFamily = brandFamily,
            fontSize = ChipboxTypeScaleTokens.HeadlineSmallSize * brandScale,
            lineHeight = ChipboxTypeScaleTokens.HeadlineSmallLineHeight * brandScale,
        ),
        titleLarge = ChipboxTypographyTokens.TitleLarge.copy(
            fontFamily = brandFamily,
            fontSize = ChipboxTypeScaleTokens.TitleLargeSize * brandScale,
            lineHeight = ChipboxTypeScaleTokens.TitleLargeLineHeight * brandScale,
        ),
        titleMedium = ChipboxTypographyTokens.TitleMedium.copy(
            fontFamily = plainFamily,
            fontSize = ChipboxTypeScaleTokens.TitleMediumSize * plainScale,
            lineHeight = ChipboxTypeScaleTokens.TitleMediumLineHeight * plainScale,
        ),
        titleSmall = ChipboxTypographyTokens.TitleSmall.copy(
            fontFamily = plainFamily,
            fontSize = ChipboxTypeScaleTokens.TitleSmallSize * plainScale,
            lineHeight = ChipboxTypeScaleTokens.TitleSmallLineHeight * plainScale,
        ),
        bodyLarge = ChipboxTypographyTokens.BodyLarge.copy(
            fontFamily = plainFamily,
            fontSize = ChipboxTypeScaleTokens.BodyLargeSize * plainScale,
            lineHeight = ChipboxTypeScaleTokens.BodyLargeLineHeight * plainScale,
        ),
        bodyMedium = ChipboxTypographyTokens.BodyMedium.copy(
            fontFamily = plainFamily,
            fontSize = ChipboxTypeScaleTokens.BodyMediumSize * plainScale,
            lineHeight = ChipboxTypeScaleTokens.BodyMediumLineHeight * plainScale,
        ),
        bodySmall = ChipboxTypographyTokens.BodySmall.copy(
            fontFamily = plainFamily,
            fontSize = ChipboxTypeScaleTokens.BodySmallSize * plainScale,
            lineHeight = ChipboxTypeScaleTokens.BodySmallLineHeight * plainScale,
        ),
        labelLarge = ChipboxTypographyTokens.LabelLarge.copy(
            fontFamily = plainFamily,
            fontSize = ChipboxTypeScaleTokens.LabelLargeSize * plainScale,
            lineHeight = ChipboxTypeScaleTokens.LabelLargeLineHeight * plainScale,
        ),
        labelMedium = ChipboxTypographyTokens.LabelMedium.copy(
            fontFamily = plainFamily,
            fontSize = ChipboxTypeScaleTokens.LabelMediumSize * plainScale,
            lineHeight = ChipboxTypeScaleTokens.LabelMediumLineHeight * plainScale,
        ),
        labelSmall = ChipboxTypographyTokens.LabelSmall.copy(
            fontFamily = plainFamily,
            fontSize = ChipboxTypeScaleTokens.LabelSmallSize * plainScale,
            lineHeight = ChipboxTypeScaleTokens.LabelSmallLineHeight * plainScale,
        ),
    )
}
