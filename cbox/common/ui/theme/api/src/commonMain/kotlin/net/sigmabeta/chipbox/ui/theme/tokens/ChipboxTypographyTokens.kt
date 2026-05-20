package net.sigmabeta.chipbox.ui.theme.tokens

import androidx.compose.ui.text.TextStyle

internal object ChipboxTypographyTokens {
    val BodyLarge =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.BodyLargeFont,
            fontWeight = ChipboxTypeScaleTokens.BodyLargeWeight,
            fontSize = ChipboxTypeScaleTokens.BodyLargeSize,
            lineHeight = ChipboxTypeScaleTokens.BodyLargeLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.BodyLargeTracking,
        )
    val BodyMedium =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.BodyMediumFont,
            fontWeight = ChipboxTypeScaleTokens.BodyMediumWeight,
            fontSize = ChipboxTypeScaleTokens.BodyMediumSize,
            lineHeight = ChipboxTypeScaleTokens.BodyMediumLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.BodyMediumTracking,
        )
    val BodySmall =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.BodySmallFont,
            fontWeight = ChipboxTypeScaleTokens.BodySmallWeight,
            fontSize = ChipboxTypeScaleTokens.BodySmallSize,
            lineHeight = ChipboxTypeScaleTokens.BodySmallLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.BodySmallTracking,
        )
    val DisplayLarge =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.DisplayLargeFont,
            fontWeight = ChipboxTypeScaleTokens.DisplayLargeWeight,
            fontSize = ChipboxTypeScaleTokens.DisplayLargeSize,
            lineHeight = ChipboxTypeScaleTokens.DisplayLargeLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.DisplayLargeTracking,
        )
    val DisplayMedium =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.DisplayMediumFont,
            fontWeight = ChipboxTypeScaleTokens.DisplayMediumWeight,
            fontSize = ChipboxTypeScaleTokens.DisplayMediumSize,
            lineHeight = ChipboxTypeScaleTokens.DisplayMediumLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.DisplayMediumTracking,
        )
    val DisplaySmall =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.DisplaySmallFont,
            fontWeight = ChipboxTypeScaleTokens.DisplaySmallWeight,
            fontSize = ChipboxTypeScaleTokens.DisplaySmallSize,
            lineHeight = ChipboxTypeScaleTokens.DisplaySmallLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.DisplaySmallTracking,
        )
    val HeadlineLarge =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.HeadlineLargeFont,
            fontWeight = ChipboxTypeScaleTokens.HeadlineLargeWeight,
            fontSize = ChipboxTypeScaleTokens.HeadlineLargeSize,
            lineHeight = ChipboxTypeScaleTokens.HeadlineLargeLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.HeadlineLargeTracking,
        )
    val HeadlineMedium =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.HeadlineMediumFont,
            fontWeight = ChipboxTypeScaleTokens.HeadlineMediumWeight,
            fontSize = ChipboxTypeScaleTokens.HeadlineMediumSize,
            lineHeight = ChipboxTypeScaleTokens.HeadlineMediumLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.HeadlineMediumTracking,
        )
    val HeadlineSmall =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.HeadlineSmallFont,
            fontWeight = ChipboxTypeScaleTokens.HeadlineSmallWeight,
            fontSize = ChipboxTypeScaleTokens.HeadlineSmallSize,
            lineHeight = ChipboxTypeScaleTokens.HeadlineSmallLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.HeadlineSmallTracking,
        )
    val LabelLarge =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.LabelLargeFont,
            fontWeight = ChipboxTypeScaleTokens.LabelLargeWeight,
            fontSize = ChipboxTypeScaleTokens.LabelLargeSize,
            lineHeight = ChipboxTypeScaleTokens.LabelLargeLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.LabelLargeTracking,
        )
    val LabelMedium =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.LabelMediumFont,
            fontWeight = ChipboxTypeScaleTokens.LabelMediumWeight,
            fontSize = ChipboxTypeScaleTokens.LabelMediumSize,
            lineHeight = ChipboxTypeScaleTokens.LabelMediumLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.LabelMediumTracking,
        )
    val LabelSmall =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.LabelSmallFont,
            fontWeight = ChipboxTypeScaleTokens.LabelSmallWeight,
            fontSize = ChipboxTypeScaleTokens.LabelSmallSize,
            lineHeight = ChipboxTypeScaleTokens.LabelSmallLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.LabelSmallTracking,
        )
    val TitleLarge =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.TitleLargeFont,
            fontWeight = ChipboxTypeScaleTokens.TitleLargeWeight,
            fontSize = ChipboxTypeScaleTokens.TitleLargeSize,
            lineHeight = ChipboxTypeScaleTokens.TitleSmallLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.TitleLargeTracking,
        )
    val TitleMedium =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.TitleMediumFont,
            fontWeight = ChipboxTypeScaleTokens.TitleMediumWeight,
            fontSize = ChipboxTypeScaleTokens.TitleMediumSize,
            lineHeight = ChipboxTypeScaleTokens.TitleMediumLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.TitleMediumTracking,
        )
    val TitleSmall =
        DefaultTextStyle.copy(
            fontFamily = ChipboxTypeScaleTokens.TitleSmallFont,
            fontWeight = ChipboxTypeScaleTokens.TitleSmallWeight,
            fontSize = ChipboxTypeScaleTokens.TitleSmallSize,
            lineHeight = ChipboxTypeScaleTokens.TitleSmallLineHeight,
            letterSpacing = ChipboxTypeScaleTokens.TitleSmallTracking,
        )
}

/**
 * Base [TextStyle] every Chipbox `Typography` slot copies from. Android applies a
 * `PlatformTextStyle(includeFontPadding = false)` tweak (the legacy
 * `android:includeFontPadding` default added extra vertical space around text); JVM/desktop
 * has no such concept, so its actual is just `TextStyle.Default`. Declared as a function
 * rather than a val because `expect val` is still a Beta language feature.
 */
internal expect fun chipboxDefaultTextStyle(): TextStyle

internal val DefaultTextStyle = chipboxDefaultTextStyle()
