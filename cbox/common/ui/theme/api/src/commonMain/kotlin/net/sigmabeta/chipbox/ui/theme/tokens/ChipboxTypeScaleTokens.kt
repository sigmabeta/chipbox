package net.sigmabeta.chipbox.ui.theme.api.tokens

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// The *Font members below are placeholders — `buildChipboxTypography` always overrides them
// via `.copy(fontFamily = ...)` with the caller-supplied brand / plain families (on Android,
// derived from a `ChipboxFont`; on JVM, currently `FontFamily.Default`). Keeping them as
// FontFamily.Default here lets this file live in commonMain without dragging the Android-only
// `Font(resId)` factory in.
internal object ChipboxTypeScaleTokens {
    val BodyLargeFont = FontFamily.Default
    val BodyLargeLineHeight = 24.0.sp
    val BodyLargeSize = 16.sp
    val BodyLargeTracking = 0.5.sp
    val BodyLargeWeight = ChipboxTypefaceTokens.WeightRegular
    val BodyMediumFont = FontFamily.Default
    val BodyMediumLineHeight = 20.0.sp
    val BodyMediumSize = 14.sp
    val BodyMediumTracking = 0.2.sp
    val BodyMediumWeight = ChipboxTypefaceTokens.WeightRegular
    val BodySmallFont = FontFamily.Default
    val BodySmallLineHeight = 16.0.sp
    val BodySmallSize = 12.sp
    val BodySmallTracking = 0.4.sp
    val BodySmallWeight = ChipboxTypefaceTokens.WeightRegular
    val DisplayLargeFont = FontFamily.Default
    val DisplayLargeLineHeight = 64.0.sp
    val DisplayLargeSize = 57.sp
    val DisplayLargeTracking = -0.2.sp
    val DisplayLargeWeight = ChipboxTypefaceTokens.WeightRegular
    val DisplayMediumFont = FontFamily.Default
    val DisplayMediumLineHeight = 52.0.sp
    val DisplayMediumSize = 45.sp
    val DisplayMediumTracking = 0.0.sp
    val DisplayMediumWeight = ChipboxTypefaceTokens.WeightRegular
    val DisplaySmallFont = FontFamily.Default
    val DisplaySmallLineHeight = 44.0.sp
    val DisplaySmallSize = 36.sp
    val DisplaySmallTracking = 0.0.sp
    val DisplaySmallWeight = ChipboxTypefaceTokens.WeightRegular
    val HeadlineLargeFont = FontFamily.Default
    val HeadlineLargeLineHeight = 40.0.sp
    val HeadlineLargeSize = 32.sp
    val HeadlineLargeTracking = 0.0.sp
    val HeadlineLargeWeight = ChipboxTypefaceTokens.WeightRegular
    val HeadlineMediumFont = FontFamily.Default
    val HeadlineMediumLineHeight = 36.0.sp
    val HeadlineMediumSize = 28.sp
    val HeadlineMediumTracking = 0.0.sp
    val HeadlineMediumWeight = ChipboxTypefaceTokens.WeightRegular
    val HeadlineSmallFont = FontFamily.Default
    val HeadlineSmallLineHeight = 32.0.sp
    val HeadlineSmallSize = 24.sp
    val HeadlineSmallTracking = 0.0.sp
    val HeadlineSmallWeight = ChipboxTypefaceTokens.WeightRegular
    val LabelLargeFont = FontFamily.Default
    val LabelLargeLineHeight = 20.0.sp
    val LabelLargeSize = 14.sp
    val LabelLargeTracking = 0.1.sp
    val LabelLargeWeight = ChipboxTypefaceTokens.WeightMedium
    val LabelMediumFont = FontFamily.Default
    val LabelMediumLineHeight = 16.0.sp
    val LabelMediumSize = 12.sp
    val LabelMediumTracking = 0.5.sp
    val LabelMediumWeight = ChipboxTypefaceTokens.WeightMedium
    val LabelSmallFont = FontFamily.Default
    val LabelSmallLineHeight = 16.0.sp
    val LabelSmallSize = 11.sp
    val LabelSmallTracking = 0.5.sp
    val LabelSmallWeight = ChipboxTypefaceTokens.WeightMedium
    val TitleLargeFont = FontFamily.Default
    val TitleLargeLineHeight = 28.0.sp
    val TitleLargeSize = 22.sp
    val TitleLargeTracking = 0.0.sp
    val TitleLargeWeight = ChipboxTypefaceTokens.WeightRegular
    val TitleMediumFont = FontFamily.Default
    val TitleMediumLineHeight = 24.0.sp
    val TitleMediumSize = 16.sp
    val TitleMediumTracking = 0.2.sp
    val TitleMediumWeight = ChipboxTypefaceTokens.WeightMedium
    val TitleSmallFont = FontFamily.Default
    val TitleSmallLineHeight = 20.0.sp
    val TitleSmallSize = 14.sp
    val TitleSmallTracking = 0.1.sp
    val TitleSmallWeight = ChipboxTypefaceTokens.WeightMedium
}
