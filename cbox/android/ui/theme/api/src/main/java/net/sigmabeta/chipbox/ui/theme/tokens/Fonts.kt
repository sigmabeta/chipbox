package net.sigmabeta.chipbox.ui.theme.tokens

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont

internal fun ChipboxFont.toFontFamily(): FontFamily = FontFamily(Font(fontResId))
