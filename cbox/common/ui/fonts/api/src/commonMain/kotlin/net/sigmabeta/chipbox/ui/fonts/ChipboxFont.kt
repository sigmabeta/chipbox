package net.sigmabeta.chipbox.ui.fonts

import androidx.compose.ui.text.font.FontFamily
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.Res
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.earthbound
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.enix
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.final_fantasy_iv
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.final_fantasy_vi
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.final_fantasy_vii
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.mega_man_x
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.nds
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.nes
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.open_dyslexic
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.ps4
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.shining_force_lg
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.shining_force_sm
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.shinobi
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.sonic1
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.sonic3
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.star_fox_64
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.super_mario_64
import net.sigmabeta.chipbox.common.ui.fonts.api.generated.resources.tloz_lttp
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

enum class ChipboxFont(
    val resource: FontResource,
    val fontName: String,
    val description: String,
    val url: String,
    val scaleFactor: Float = 1.0f,
) {
    PLANETARY(
        Res.font.earthbound,
        "Planetary",
        "A font only a mother or two could love.",
        "https://www.fontstruct.com/fontstructions/show/2241587/earthbound-dialogue-gold",
        scaleFactor = 0.98f,
    ),
    METEOR(
        Res.font.final_fantasy_vii,
        "Meteor",
        "This train is express, so you can't disembark.",
        "https://www.fontstruct.com/fontstructions/show/2148395/final-fantasy-vii",
        scaleFactor = 0.89f,
    ),
    ESPERANTO(
        Res.font.final_fantasy_vi,
        "Esperanto",
        "Balanced or ruinous.",
        "https://www.fontstruct.com/fontstructions/show/324181/final_fantasy_3_6_font",
        scaleFactor = 1.43f,
    ),
    BARON(
        Res.font.final_fantasy_iv,
        "Baron",
        "Sing all you want, if you've got the spoons.",
        "https://www.fontstruct.com/fontstructions/show/1532279/final-fantasy-iv",
        scaleFactor = 0.82f,
    ),
    UNSQUARED(
        Res.font.enix,
        "Unsquared",
        "Feels a little more than a quartet.",
        "https://www.fontstruct.com/fontstructions/show/1050074/gaiatype",
        scaleFactor = 0.88f,
    ),
    POWERFUL(
        Res.font.super_mario_64,
        "Powerful",
        "Go get the cake in the castle.",
        "https://www.fontstruct.com/fontstructions/show/1957193/super-mario-64-17",
        scaleFactor = 1.14f,
    ),
    ENERGY_MITT(
        Res.font.nes,
        "Energy Mitt",
        "Don't forget to blow on the cartridge.",
        "https://www.fontstruct.com/fontstructions/show/406653/nintendo_nes_font",
        scaleFactor = 0.82f,
    ),
    DOUBLE_TOUCH(
        Res.font.nds,
        "Double Touch",
        "Touching was good, for some games.",
        "https://www.fontstruct.com/fontstructions/show/1823618/nintendo-ds-4",
        scaleFactor = 0.79f,
    ),
    MAVERICK(
        Res.font.mega_man_x,
        "Maverick",
        "Don't wait 30 years to use this.",
        "https://www.fontstruct.com/fontstructions/show/1038596/mega_man_x_1",
        scaleFactor = 0.95f,
    ),
    THREE_POWERS(
        Res.font.tloz_lttp,
        "Three Powers",
        "Watch out for those angry chickens.",
        "https://www.fontstruct.com/fontstructions/show/1534358/the-legend-of-zelda-a-link-to-the-past-big-1",
        scaleFactor = 1.04f,
    ),
    SPACE_PETS(
        Res.font.star_fox_64,
        "Space Pets",
        "You can repel attacks by spinning.",
        "https://www.fontstruct.com/fontstructions/show/2112578/all-aircraft-report",
        scaleFactor = 0.87f,
    ),
    BRILLIANT(
        Res.font.shining_force_lg,
        "Brilliant",
        "Get promoted to the fighting class!",
        "https://www.fontstruct.com/fontstructions/show/1624093/shining-force-large",
        scaleFactor = 1.04f,
    ),
    REBEL(
        Res.font.shining_force_sm,
        "Rebel Rebel",
        "A font of great intention.",
        "https://www.fontstruct.com/fontstructions/show/1626059/shining-force-ii-small",
        scaleFactor = 0.82f,
    ),
    LANDEEL(
        Res.font.ps4,
        "Landeel",
        "Get ready for Y2K.",
        "https://www.fontstruct.com/fontstructions/show/1667381/the-promissing-future",
        scaleFactor = 0.86f,
    ),
    NINJA(
        Res.font.shinobi,
        "Ninja",
        "Stronger than steel, faster than a whirlwind.",
        "https://www.fontstruct.com/fontstructions/show/1404594/shinobi-iii-return-of-the-ninja-master",
        scaleFactor = 0.71f,
    ),
    ECHIDNA(
        Res.font.sonic3,
        "Echidna",
        "I don't chuckle; I'd rather flex my muscles.",
        "https://www.fontstruct.com/fontstructions/show/2454486/plaza-pixel-c-s3-k-title-card",
        scaleFactor = 0.95f,
    ),
    PORCUPINE(
        Res.font.sonic1,
        "Porcupine",
        "For those who require speed",
        "https://www.fontstruct.com/fontstructions/show/1320066/sonic-genesis-mega-drive-font",
        scaleFactor = 1.14f,
    ),
    DYSLEXIC(
        Res.font.open_dyslexic,
        "OpenDyslexic",
        "No clever quip here, just the goods.",
        "https://opendyslexic.org/",
        scaleFactor = 0.84f,
    ),
    ;

    /**
     * Multiplatform `FontFamily` for this entry. The CMP resources codegen produces the
     * appropriate per-target [Font] (Android: packaged into the AAR's `composeResources`;
     * JVM/desktop: bundled on the classpath under `composeResources/font/`).
     *
     * `@Composable` because the underlying CMP `Font(FontResource)` factory is — font loading
     * happens lazily and is bound to the composition's lifecycle.
     */
    @androidx.compose.runtime.Composable
    fun toFontFamily(): FontFamily = FontFamily(Font(resource))

    companion object {
        /** Brand/plain typefaces applied until the user picks otherwise in Settings. */
        val DEFAULT_BRAND: ChipboxFont = DOUBLE_TOUCH
        val DEFAULT_PLAIN: ChipboxFont = PLANETARY

        /** Resolves a persisted [ChipboxFont.name] back to an entry, falling back to [default]. */
        fun fromStorageValue(value: String?, default: ChipboxFont): ChipboxFont =
            entries.firstOrNull { it.name == value } ?: default
    }
}
