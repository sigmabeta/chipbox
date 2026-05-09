package net.sigmabeta.chipbox.ui.theme

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.io.File
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.chipbox.ui.fonts.R
import org.junit.Rule
import org.junit.Test

class FontScalePaparazziTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun measureScaleFactors() {
        val frc = FontRenderContext(
            null,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
            RenderingHints.VALUE_FRACTIONALMETRICS_ON,
        )
        val refFont = Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(100f)
        val refHeight = refFont.glyphCapHeight(frc)

        ChipboxFont.entries.forEach { font ->
            val file = font.resolveOtfFile()
            val awtFont = Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(100f)
            val h = awtFont.glyphCapHeight(frc)
            val sf = "%.2f".format(refHeight / h)
            println("    ${font.name}(scaleFactor = ${sf}f),")
        }
    }
}

private fun Font.glyphCapHeight(frc: FontRenderContext): Double {
    val gv = createGlyphVector(frc, "H")
    return gv.getVisualBounds().height
}

private fun ChipboxFont.resolveOtfFile(): File {
    val fieldName = R.font::class.java.declaredFields
        .first { it.getInt(null) == fontResId }
        .name
    // Paparazzi resources.json lists font module resources as relative to the theme module dir.
    // User.dir in Gradle unit tests is the project root.
    val moduleDir = File(System.getProperty("user.dir"))
    val candidates = listOf(
        moduleDir.resolve("../fonts/src/main/res/font/$fieldName.otf"),
        moduleDir.resolve("../fonts/build/intermediates/packaged_res/debug/packageDebugResources/font/$fieldName.otf"),
    )
    return candidates.firstOrNull { it.exists() }
        ?: error("OTF file not found for $name (tried: ${candidates.map { it.path }})")
}
