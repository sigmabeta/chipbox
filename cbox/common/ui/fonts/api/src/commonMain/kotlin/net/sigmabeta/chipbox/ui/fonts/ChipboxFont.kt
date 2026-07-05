package net.sigmabeta.chipbox.ui.fonts

/**
 * Pure metadata enum for the 18 Chipbox pixel-art fonts — name + description + URL. The Compose
 * binding (FontResource lookup + `toFontFamily()` extension + the .otf assets) lives in the
 * sibling `:real` module, so this enum stays Compose-free. Consumers that just carry a
 * `ChipboxFont` value through actions/state (e.g. [SettingsAction.BrandFontSelected],
 * [ChipboxAppUiViewModel]) can depend on `:api` alone — meaningful on Kotlin/JS, where loading
 * Compose-Resources would trigger Skiko at class-init time.
 */
enum class ChipboxFont(
    val fontName: String,
    val description: String,
    val url: String,
) {
    REGULAR(
        "Regular",
        "Looks like any other regular app.",
        "https://rsms.me/inter/"
    ),
    PLANETARY(
        "Planetary",
        "A font only a mother or two could love.",
        "https://www.fontstruct.com/fontstructions/show/2241587/earthbound-dialogue-gold",
    ),
    METEOR(
        "Meteor",
        "This train is express, so you can't disembark.",
        "https://www.fontstruct.com/fontstructions/show/2148395/final-fantasy-vii",
    ),
    ESPERANTO(
        "Esperanto",
        "Balanced or ruinous.",
        "https://www.fontstruct.com/fontstructions/show/324181/final_fantasy_3_6_font",
    ),
    BARON(
        "Baron",
        "Sing all you want, if you've got the spoons.",
        "https://www.fontstruct.com/fontstructions/show/1532279/final-fantasy-iv",
    ),
    UNSQUARED(
        "Unsquared",
        "Feels a little more than a quartet.",
        "https://www.fontstruct.com/fontstructions/show/1050074/gaiatype",
    ),
    POWERFUL(
        "Powerful",
        "Go get the cake in the castle.",
        "https://www.fontstruct.com/fontstructions/show/1957193/super-mario-64-17",
    ),
    ENERGY_MITT(
        "Energy Mitt",
        "Don't forget to blow on the cartridge.",
        "https://www.fontstruct.com/fontstructions/show/406653/nintendo_nes_font",
    ),
    DOUBLE_TOUCH(
        "Double Touch",
        "Touching was good, for some games.",
        "https://www.fontstruct.com/fontstructions/show/1823618/nintendo-ds-4",
    ),
    MAVERICK(
        "Maverick",
        "Don't wait 30 years to use this.",
        "https://www.fontstruct.com/fontstructions/show/1038596/mega_man_x_1",
    ),
    THREE_POWERS(
        "Three Powers",
        "Watch out for those angry chickens.",
        "https://www.fontstruct.com/fontstructions/show/1534358/the-legend-of-zelda-a-link-to-the-past-big-1",
    ),
    SPACE_PETS(
        "Space Pets",
        "You can repel attacks by spinning.",
        "https://www.fontstruct.com/fontstructions/show/2112578/all-aircraft-report",
    ),
    BRILLIANT(
        "Brilliant",
        "Get promoted to the fighting class!",
        "https://www.fontstruct.com/fontstructions/show/1624093/shining-force-large",
    ),
    REBEL(
        "Rebel Rebel",
        "A font of great intention.",
        "https://www.fontstruct.com/fontstructions/show/1626059/shining-force-ii-small",
    ),
    LANDEEL(
        "Landeel",
        "Get ready for Y2K.",
        "https://www.fontstruct.com/fontstructions/show/1667381/the-promissing-future",
    ),
    NINJA(
        "Ninja",
        "Stronger than steel, faster than a whirlwind.",
        "https://www.fontstruct.com/fontstructions/show/1404594/shinobi-iii-return-of-the-ninja-master",
    ),
    ECHIDNA(
        "Echidna",
        "I don't chuckle; I'd rather flex my muscles.",
        "https://www.fontstruct.com/fontstructions/show/2454486/plaza-pixel-c-s3-k-title-card",
    ),
    PORCUPINE(
        "Porcupine",
        "For those who require speed",
        "https://www.fontstruct.com/fontstructions/show/1320066/sonic-genesis-mega-drive-font",
    ),
    DYSLEXIC(
        "OpenDyslexic",
        "Helps with readability.",
        "https://opendyslexic.org/",
    ),
    ;

    companion object {
        /** Brand/plain typefaces applied until the user picks otherwise in Settings. */
        val DEFAULT_BRAND: ChipboxFont = REGULAR
        val DEFAULT_PLAIN: ChipboxFont = REGULAR

        /** Resolves a persisted [ChipboxFont.name] back to an entry, falling back to [default]. */
        fun fromStorageValue(value: String?, default: ChipboxFont): ChipboxFont =
            entries.firstOrNull { it.name == value } ?: default
    }
}
