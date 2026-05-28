package net.sigmabeta.chipbox.ui.theme.api

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import net.sigmabeta.chipbox.ui.theme.api.tokens.ChipboxTypeScaleTokens
import net.sigmabeta.chipbox.ui.theme.api.tokens.ChipboxTypographyTokens

/**
 * Build a Material3 [Typography] from Chipbox's type scale tokens with caller-supplied font
 * families. Android passes families derived from a `ChipboxFont` (via the Android-only
 * `Font(resId)` factory); the JVM/desktop entry currently passes `FontFamily.Default` because
 * the font-resource story (Compose-MP resources vs `expect`/`actual` `FontFamily`) hasn't been
 * picked yet — only the type *structure* (sizes, weights, line heights) is shared today.
 *
 * `scale` is caller-supplied so each platform can apply a user-facing font scale uniformly
 * (system accessibility scale on Android; passed through on JVM).
 */
@Suppress("LongMethod")
fun buildChipboxTypography(
    brand: FontFamily = FontFamily.Default,
    plain: FontFamily = FontFamily.Default,
    scale: Float = 1.0f,
): Typography = Typography(
    displayLarge = ChipboxTypographyTokens.DisplayLarge.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.DisplayLargeSize * scale,
        lineHeight = ChipboxTypeScaleTokens.DisplayLargeLineHeight * scale,
    ),
    displayMedium = ChipboxTypographyTokens.DisplayMedium.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.DisplayMediumSize * scale,
        lineHeight = ChipboxTypeScaleTokens.DisplayMediumLineHeight * scale,
    ),
    displaySmall = ChipboxTypographyTokens.DisplaySmall.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.DisplaySmallSize * scale,
        lineHeight = ChipboxTypeScaleTokens.DisplaySmallLineHeight * scale,
    ),
    headlineLarge = ChipboxTypographyTokens.HeadlineLarge.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.HeadlineLargeSize * scale,
        lineHeight = ChipboxTypeScaleTokens.HeadlineLargeLineHeight * scale,
    ),
    headlineMedium = ChipboxTypographyTokens.HeadlineMedium.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.HeadlineMediumSize * scale,
        lineHeight = ChipboxTypeScaleTokens.HeadlineMediumLineHeight * scale,
    ),
    headlineSmall = ChipboxTypographyTokens.HeadlineSmall.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.HeadlineSmallSize * scale,
        lineHeight = ChipboxTypeScaleTokens.HeadlineSmallLineHeight * scale,
    ),
    titleLarge = ChipboxTypographyTokens.TitleLarge.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.TitleLargeSize * scale,
        lineHeight = ChipboxTypeScaleTokens.TitleLargeLineHeight * scale,
    ),
    titleMedium = ChipboxTypographyTokens.TitleMedium.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.TitleMediumSize * scale,
        lineHeight = ChipboxTypeScaleTokens.TitleMediumLineHeight * scale,
    ),
    titleSmall = ChipboxTypographyTokens.TitleSmall.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.TitleSmallSize * scale,
        lineHeight = ChipboxTypeScaleTokens.TitleSmallLineHeight * scale,
    ),
    bodyLarge = ChipboxTypographyTokens.BodyLarge.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.BodyLargeSize * scale,
        lineHeight = ChipboxTypeScaleTokens.BodyLargeLineHeight * scale,
    ),
    bodyMedium = ChipboxTypographyTokens.BodyMedium.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.BodyMediumSize * scale,
        lineHeight = ChipboxTypeScaleTokens.BodyMediumLineHeight * scale,
    ),
    bodySmall = ChipboxTypographyTokens.BodySmall.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.BodySmallSize * scale,
        lineHeight = ChipboxTypeScaleTokens.BodySmallLineHeight * scale,
    ),
    labelLarge = ChipboxTypographyTokens.LabelLarge.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.LabelLargeSize * scale,
        lineHeight = ChipboxTypeScaleTokens.LabelLargeLineHeight * scale,
    ),
    labelMedium = ChipboxTypographyTokens.LabelMedium.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.LabelMediumSize * scale,
        lineHeight = ChipboxTypeScaleTokens.LabelMediumLineHeight * scale,
    ),
    labelSmall = ChipboxTypographyTokens.LabelSmall.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.LabelSmallSize * scale,
        lineHeight = ChipboxTypeScaleTokens.LabelSmallLineHeight * scale,
    ),
)
