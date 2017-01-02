package net.sigmabeta.chipbox.model.domain


import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import net.sigmabeta.chipbox.model.database.save

open class Game() : RealmObject() {
    constructor(title: String, platform: Long) : this() {
        this.title = title
        this.platform = platform
    }

    @PrimaryKey var id: Long? = null

    var title: String? = null
    var platform: Long? = null

    var artLocal: String? = null
    var artWeb: String? = null
    var company: String? = null
    var multipleArtists: Boolean? = null
    var artist: Artist? = null
        get() {
            if (multipleArtists ?: false) {
                return Artist("Various Artists")
            } else {
                return field
            }
        }

    var tracks: RealmList<Track>? = null

    companion object {
        val PICASSO_PREFIX = "file://"

        val ASSET_ALBUM_ART_BLANK = "/android_asset/img_album_art_blank.png"

        val PICASSO_ASSET_ALBUM_ART_BLANK = PICASSO_PREFIX + ASSET_ALBUM_ART_BLANK

        fun addLocalImage(gameId: Long, artLocal: String) {
            val realm = Realm.getDefaultInstance()
            val game = realm.where(Game::class.java).equalTo("id", gameId).findFirst()

            game.artLocal = artLocal
            game.save()
        }
    }
}