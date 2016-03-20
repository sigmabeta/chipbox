package net.sigmabeta.chipbox.model.domain

import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase

@Table(database = ChipboxDatabase::class, allFields = true)
class Game() : BaseModel() {
    constructor(title: String, platform: Long) : this() {
        this.title = title
        this.platform = platform
    }

    @PrimaryKey (autoincrement = true) var id: Long? = null
    var title: String? = null
    var platform: Long? = null
    var artLocal: String? = null
    var artWeb: String? = null
    var company: String? = null

    companion object {
        val PICASSO_PREFIX = "file://"

        val ASSET_ALBUM_ART_BLANK = "/android_asset/img_album_art_blank.png"

        val PICASSO_ASSET_ALBUM_ART_BLANK = PICASSO_PREFIX + ASSET_ALBUM_ART_BLANK
    }
}