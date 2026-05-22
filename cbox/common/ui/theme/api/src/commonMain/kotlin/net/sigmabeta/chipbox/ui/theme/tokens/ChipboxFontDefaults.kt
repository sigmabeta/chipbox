package net.sigmabeta.chipbox.ui.theme.api.tokens

import net.sigmabeta.chipbox.ui.fonts.ChipboxFont

/**
 * Default `ChipboxFont`s wired into [net.sigmabeta.chipbox.ui.theme.api.AppTheme] (Android) and
 * picked up by [net.sigmabeta.chipbox.jvm.DesktopMain]'s desktop entry too. Public because
 * the JVM entry needs to reach `Brand`/`Plain` from `apps/jvm`; the `ChipboxFont` enum itself
 * is now multiplatform (CMP-resource-backed), so both platforms render the same pixel-art font.
 */
object ChipboxFontDefaults {
    val Brand: ChipboxFont = ChipboxFont.DEFAULT_BRAND
    val Plain: ChipboxFont = ChipboxFont.DEFAULT_PLAIN
}
