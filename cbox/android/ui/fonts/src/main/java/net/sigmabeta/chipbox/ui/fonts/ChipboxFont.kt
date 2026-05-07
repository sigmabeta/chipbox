package net.sigmabeta.chipbox.ui.fonts

enum class ChipboxFont(
    val fontResId: Int,
    val fontName: String,
    val description: String,
    val url: String,
) {
    EARTHBOUND(
        R.font.earthbound_dialogue_gold,
        "Planetary",
        "A font only a mother or two could love.",
        "https://www.fontstruct.com/fontstructions/show/2241587/earthbound-dialogue-gold"
    ),
    FF7(
        R.font.final_fantasy_vii,
        "Meteor",
        "If you don't want to get off this train.",
        "https://www.fontstruct.com/fontstructions/show/2148395/final-fantasy-vii"
    )
}