package net.sigmabeta.chipbox.model.domain

import com.raizlabs.android.dbflow.annotation.*
import com.raizlabs.android.dbflow.sql.language.SQLite
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import rx.Observable
import java.util.*

@ModelContainer
@Table(database = ChipboxDatabase::class, allFields = true, indexGroups = arrayOf(IndexGroup(number = 1, name = "titlePlatform")))
class Game() : BaseModel() {
    constructor(title: String, platform: Long) : this() {
        this.title = title
        this.platform = platform
    }

    @PrimaryKey (autoincrement = true) var id: Long? = null

    @Index(indexGroups = intArrayOf(1)) var title: String? = null
    @Index(indexGroups = intArrayOf(1)) var platform: Long? = null

    var artLocal: String? = null
    var artWeb: String? = null
    var company: String? = null
    var multipleArtists: Boolean? = null

    @ForeignKey var artist: Artist? = null
        get() {
            if (multipleArtists ?: false) {
                return Artist("Various Artists")
            } else {
                return field
            }
        }

    @ColumnIgnore
    @JvmField
    var tracks: MutableList<Track>? = null

    @OneToMany(methods = arrayOf(OneToMany.Method.SAVE, OneToMany.Method.DELETE))
    fun getTracks(): MutableList<Track> {
        this.tracks?.let {
            if (!it.isEmpty()) {
                return it
            }
        }

        val tracks = SQLite.select()
                .from(Track::class.java)
                .where(Track_Table.gameContainer_id.eq(id))
                .queryList()

        this.tracks = tracks
        return tracks
    }

    companion object {
        val PICASSO_PREFIX = "file://"

        val ASSET_ALBUM_ART_BLANK = "/android_asset/img_album_art_blank.png"

        val PICASSO_ASSET_ALBUM_ART_BLANK = PICASSO_PREFIX + ASSET_ALBUM_ART_BLANK

        fun getFromTrackList(tracks: List<Track>): Observable<HashMap<Long, Game>> {
            return Observable.create {
                logInfo("[Game] Getting games for currently displayed tracks...")
                val startTime = System.currentTimeMillis()

                val games = HashMap<Long, Game>()

                tracks.forEach { track ->
                    track.gameContainer?.toModel()?.id?.let { id ->
                        if (games[id] != null) {
                            return@forEach
                        }

                        val game = SQLite.select()
                                .from(Game::class.java)
                                .where(Game_Table.id.eq(id))
                                .querySingle()

                        if (game != null) {
                            games.put(id, game)
                            return@forEach

                        } else {
                            it.onError(Exception("Couldn't find game."))
                            return@create
                        }
                    }

                    it.onError(Exception("Bad game ID."))
                    return@create
                }

                logVerbose("[Game] Game map size: ${games.size}")

                val endTime = System.currentTimeMillis()
                val scanDuration = (endTime - startTime) / 1000.0f

                logInfo("[Game] Found games in ${scanDuration} seconds.")

                it.onNext(games)
                it.onCompleted()
            }
        }

        fun addLocalImage(gameId: Long, artLocal: String) {
            SQLite.update(Game::class.java)
                    .set(Game_Table.artLocal.eq(artLocal))
                    .where(Game_Table.id.eq(gameId))
                    .query()
        }
    }
}