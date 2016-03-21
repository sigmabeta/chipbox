package net.sigmabeta.chipbox.model.domain

import android.media.MediaMetadata
import android.support.v4.media.MediaMetadataCompat
import com.raizlabs.android.dbflow.annotation.ColumnIgnore
import com.raizlabs.android.dbflow.annotation.ForeignKey
import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.config.FlowManager
import com.raizlabs.android.dbflow.sql.language.SQLite
import com.raizlabs.android.dbflow.structure.BaseModel
import com.raizlabs.android.dbflow.structure.container.ForeignKeyContainer
import net.sigmabeta.chipbox.ChipboxDatabase
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import rx.Observable
import java.util.*

@Table(database = ChipboxDatabase::class, allFields = true)
class Track() : BaseModel() {
    constructor(number: Int,
                path: String,
                title: String,
                gameTitle: String,
                artist: String,
                platform: Long,
                trackLength: Long,
                introLength: Long,
                loopLength: Long) : this() {
        this.trackNumber = number
        this.path = path
        this.title = title
        this.gameTitle = gameTitle
        this.artistText = artist
        this.platform = platform
        this.trackLength = trackLength
        this.introLength = introLength
        this.loopLength = loopLength
    }

    @PrimaryKey (autoincrement = true) var id: Long? = null
    var trackNumber: Int? = null
    var path: String? = null
    var title: String? = null
    var platform: Long? = null
    var artistText: String? = null
    var trackLength: Long? = null
    var introLength: Long? = null
    var loopLength: Long? = null

    @ColumnIgnore
    var gameTitle: String? = null

    @ForeignKey (saveForeignKeyModel = false)
    var artistContainer: ForeignKeyContainer<Artist>? = null

    @ForeignKey (saveForeignKeyModel = false)
    var gameContainer: ForeignKeyContainer<Game>? = null

    fun associateArtist(artist: Artist) {
        artistContainer = FlowManager
                .getContainerAdapter(Artist::class.java)
                .toForeignKeyContainer(artist)
    }

    fun associateGame(game: Game) {
        gameContainer = FlowManager
                .getContainerAdapter(Game::class.java)
                .toForeignKeyContainer(game)
    }

    companion object {
        val PLATFORM_UNSUPPORTED = 100L
        val PLATFORM_ALL = -2L
        val PLATFORM_UNDEFINED = -1L
        val PLATFORM_GENESIS = 1L
        val PLATFORM_32X = 2L
        val PLATFORM_SNES = 3L
        val PLATFORM_NES = 4L
        val PLATFORM_GAMEBOY = 5L

        fun getAll(): Observable<List<Track>> {
            return Observable.create {
                logInfo("[Track] Reading song list...")

                val tracks = SQLite.select().from(Track::class.java)
                        .where()
                        .orderBy(Track_Table.title, true)
                        .queryList()

                logVerbose("[Track] Found ${tracks.size} tracks.")

                it.onNext(tracks)
                it.onCompleted()
            }
        }

        fun toMetadataBuilder(track: Track): MediaMetadataCompat.Builder {
            return MediaMetadataCompat.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, track.gameContainer?.toModel()?.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artistText)
        }

        fun addToDatabase(artistMap: HashMap<Long, Artist>, gameMap: HashMap<Long, Game>, track: Track): Long {
            var game = Game.get(track.gameTitle, track.platform, gameMap)
            val artist = Artist.get(track.artistText, artistMap)

            track.associateGame(game)
            track.associateArtist(artist)

            track.insert()
            return game.id!!
        }
    }
}