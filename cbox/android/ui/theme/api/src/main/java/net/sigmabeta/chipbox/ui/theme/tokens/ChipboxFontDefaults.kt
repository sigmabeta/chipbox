package net.sigmabeta.chipbox.ui.theme.tokens

import net.sigmabeta.chipbox.ui.fonts.ChipboxFont

/**
 * Android-side default `ChipboxFont`s wired into [net.sigmabeta.chipbox.ui.theme.AppTheme]
 * (and consumed by the Paparazzi font previews). Sat in `ChipboxTypefaceTokens` until the
 * weight constants there moved to commonMain — `Brand` and `Plain` reference
 * Android-resource-backed `ChipboxFont` entries, so they can't follow.
 */
internal object ChipboxFontDefaults {
    val Brand: ChipboxFont = ChipboxFont.DOUBLE_TOUCH
    val Plain: ChipboxFont = ChipboxFont.PLANETARY
}
