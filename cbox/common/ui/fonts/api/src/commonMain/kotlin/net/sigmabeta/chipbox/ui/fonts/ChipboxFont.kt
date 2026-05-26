package net.sigmabeta.chipbox.ui.fonts

/**
 * Pure metadata enum for the 18 Chipbox pixel-art fonts — name + description + URL + scale
 * factor. The Compose binding (FontResource lookup + `toFontFamily()` extension + the .otf
 * assets) lives in the sibling `:real` module, so this enum stays Compose-free. Consumers that
 * just carry a `ChipboxFont` value through actions/state (e.g. [SettingsAction.BrandFontSelected],
 * [ChipboxAppUiViewModel]) can depend on `:api` alone — meaningful on Kotlin/JS, where loading
 * Compose-Resources would trigger Skiko at class-init time.
 */
enum class ChipboxFont(
    val fontName: String,
    val description: String,
    val url: String,
    val scaleFactor: Float = 1.0f,
) {
    PLANETARY(
        "Planetary",
        "A font only a mother or two could love.",
        "https://www.fontstruct.com/fontstructions/show/2241587/earthbound-dialogue-gold",
        scaleFactor = 0.98f,
    ),
    METEOR(
        "Meteor",
        "This train is express, so you can't disembark.",
        "https://www.fontstruct.com/fontstructions/show/2148395/final-fantasy-vii",
        scaleFactor = 0.89f,
    ),
    ESPERANTO(
        "Esperanto",
        "Balanced or ruinous.",
        "https://www.fontstruct.com/fontstructions/show/324181/final_fantasy_3_6_font",
        scaleFactor = 1.43f,
    ),
    BARON(
        "Baron",
        "Sing all you want, if you've got the spoons.",
        "https://www.fontstruct.com/fontstructions/show/1532279/final-fantasy-iv",
        scaleFactor = 0.82f,
    ),
    UNSQUARED(
        "Unsquared",
        "Feels a little more than a quartet.",
        "https://www.fontstruct.com/fontstructions/show/1050074/gaiatype",
        scaleFactor = 0.88f,
    ),
    POWERFUL(
        "Powerful",
        "Go get the cake in the castle.",
        "https://www.fontstruct.com/fontstructions/show/1957193/super-mario-64-17",
        scaleFactor = 1.14f,
    ),
    ENERGY_MITT(
        "Energy Mitt",
        "Don't forget to blow on the cartridge.",
        "https://www.fontstruct.com/fontstructions/show/406653/nintendo_nes_font",
        scaleFactor = 0.82f,
    ),
    DOUBLE_TOUCH(
        "Double Touch",
        "Touching was good, for some games.",
        "https://www.fontstruct.com/fontstructions/show/1823618/nintendo-ds-4",
        scaleFactor = 0.79f,
    ),
    MAVERICK(
        "Maverick",
        "Don't wait 30 years to use this.",
        "https://www.fontstruct.com/fontstructions/show/1038596/mega_man_x_1",
        scaleFactor = 0.95f,
    ),
    THREE_POWERS(
        "Three Powers",
        "Watch out for those angry chickens.",
        "https://www.fontstruct.com/fontstructions/show/1534358/the-legend-of-zelda-a-link-to-the-past-big-1",
        scaleFactor = 1.04f,
    ),
    SPACE_PETS(
        "Space Pets",
        "You can repel attacks by spinning.",
        "https://www.fontstruct.com/fontstructions/show/2112578/all-aircraft-report",
        scaleFactor = 0.87f,
    ),
    BRILLIANT(
        "Brilliant",
        "Get promoted to the fighting class!",
        "https://www.fontstruct.com/fontstructions/show/1624093/shining-force-large",
        scaleFactor = 1.04f,
    ),
    REBEL(
        "Rebel Rebel",
        "A font of great intention.",
        "https://www.fontstruct.com/fontstructions/show/1626059/shining-force-ii-small",
        scaleFactor = 0.82f,
    ),
    LANDEEL(
        "Landeel",
        "Get ready for Y2K.",
        "https://www.fontstruct.com/fontstructions/show/1667381/the-promissing-future",
        scaleFactor = 0.86f,
    ),
    NINJA(
        "Ninja",
        "Stronger than steel, faster than a whirlwind.",
        "https://www.fontstruct.com/fontstructions/show/1404594/shinobi-iii-return-of-the-ninja-master",
        scaleFactor = 0.71f,
    ),
    ECHIDNA(
        "Echidna",
        "I don't chuckle; I'd rather flex my muscles.",
        "https://www.fontstruct.com/fontstructions/show/2454486/plaza-pixel-c-s3-k-title-card",
        scaleFactor = 0.95f,
    ),
    PORCUPINE(
        "Porcupine",
        "For those who require speed",
        "https://www.fontstruct.com/fontstructions/show/1320066/sonic-genesis-mega-drive-font",
        scaleFactor = 1.14f,
    ),
    DYSLEXIC(
        "OpenDyslexic",
        "No clever quip here, just the goods.",
        "https://opendyslexic.org/",
        scaleFactor = 0.84f,
    ),
    ;

    companion object {
        /** Brand/plain typefaces applied until the user picks otherwise in Settings. */
        val DEFAULT_BRAND: ChipboxFont = DOUBLE_TOUCH
        val DEFAULT_PLAIN: ChipboxFont = PLANETARY

        /** Resolves a persisted [ChipboxFont.name] back to an entry, falling back to [default]. */
        fun fromStorageValue(value: String?, default: ChipboxFont): ChipboxFont =
            entries.firstOrNull { it.name == value } ?: default
    }
}
