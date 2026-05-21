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
 * `brandScale` / `plainScale` are caller-computed so each platform can fold its own
 * per-font scale factor in (Android multiplies the picked `ChipboxFont.scaleFactor` by a
 * user-facing `fontScale`; JVM just passes `fontScale` directly).
 */
@Suppress("LongMethod")
fun buildChipboxTypography(
    brand: FontFamily = FontFamily.Default,
    plain: FontFamily = FontFamily.Default,
    brandScale: Float = 1.0f,
    plainScale: Float = 1.0f,
): Typography = Typography(
    displayLarge = ChipboxTypographyTokens.DisplayLarge.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.DisplayLargeSize * brandScale,
        lineHeight = ChipboxTypeScaleTokens.DisplayLargeLineHeight * brandScale,
    ),
    displayMedium = ChipboxTypographyTokens.DisplayMedium.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.DisplayMediumSize * brandScale,
        lineHeight = ChipboxTypeScaleTokens.DisplayMediumLineHeight * brandScale,
    ),
    displaySmall = ChipboxTypographyTokens.DisplaySmall.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.DisplaySmallSize * brandScale,
        lineHeight = ChipboxTypeScaleTokens.DisplaySmallLineHeight * brandScale,
    ),
    headlineLarge = ChipboxTypographyTokens.HeadlineLarge.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.HeadlineLargeSize * brandScale,
        lineHeight = ChipboxTypeScaleTokens.HeadlineLargeLineHeight * brandScale,
    ),
    headlineMedium = ChipboxTypographyTokens.HeadlineMedium.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.HeadlineMediumSize * brandScale,
        lineHeight = ChipboxTypeScaleTokens.HeadlineMediumLineHeight * brandScale,
    ),
    headlineSmall = ChipboxTypographyTokens.HeadlineSmall.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.HeadlineSmallSize * brandScale,
        lineHeight = ChipboxTypeScaleTokens.HeadlineSmallLineHeight * brandScale,
    ),
    titleLarge = ChipboxTypographyTokens.TitleLarge.copy(
        fontFamily = brand,
        fontSize = ChipboxTypeScaleTokens.TitleLargeSize * brandScale,
        lineHeight = ChipboxTypeScaleTokens.TitleLargeLineHeight * brandScale,
    ),
    titleMedium = ChipboxTypographyTokens.TitleMedium.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.TitleMediumSize * plainScale,
        lineHeight = ChipboxTypeScaleTokens.TitleMediumLineHeight * plainScale,
    ),
    titleSmall = ChipboxTypographyTokens.TitleSmall.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.TitleSmallSize * plainScale,
        lineHeight = ChipboxTypeScaleTokens.TitleSmallLineHeight * plainScale,
    ),
    bodyLarge = ChipboxTypographyTokens.BodyLarge.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.BodyLargeSize * plainScale,
        lineHeight = ChipboxTypeScaleTokens.BodyLargeLineHeight * plainScale,
    ),
    bodyMedium = ChipboxTypographyTokens.BodyMedium.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.BodyMediumSize * plainScale,
        lineHeight = ChipboxTypeScaleTokens.BodyMediumLineHeight * plainScale,
    ),
    bodySmall = ChipboxTypographyTokens.BodySmall.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.BodySmallSize * plainScale,
        lineHeight = ChipboxTypeScaleTokens.BodySmallLineHeight * plainScale,
    ),
    labelLarge = ChipboxTypographyTokens.LabelLarge.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.LabelLargeSize * plainScale,
        lineHeight = ChipboxTypeScaleTokens.LabelLargeLineHeight * plainScale,
    ),
    labelMedium = ChipboxTypographyTokens.LabelMedium.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.LabelMediumSize * plainScale,
        lineHeight = ChipboxTypeScaleTokens.LabelMediumLineHeight * plainScale,
    ),
    labelSmall = ChipboxTypographyTokens.LabelSmall.copy(
        fontFamily = plain,
        fontSize = ChipboxTypeScaleTokens.LabelSmallSize * plainScale,
        lineHeight = ChipboxTypeScaleTokens.LabelSmallLineHeight * plainScale,
    ),
)
