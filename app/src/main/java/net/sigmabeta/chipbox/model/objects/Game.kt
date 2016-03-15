package net.sigmabeta.chipbox.model.objects

data class Game(
        val id: Long,
        val title: String,
        val platform: Int,
        val artLocal: String?,
        val artWeb: String?,
        val company: String?
) {
    companion object {
        val PICASSO_PREFIX = "file://"

        val ASSET_ALBUM_ART_BLANK = "/android_asset/img_album_art_blank.png"

        val PICASSO_ASSET_ALBUM_ART_BLANK = PICASSO_PREFIX + ASSET_ALBUM_ART_BLANK
    }
}