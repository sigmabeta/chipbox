package net.sigmabeta.chipbox.model.domain

import com.raizlabs.android.dbflow.annotation.*
import com.raizlabs.android.dbflow.sql.language.SQLite
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import rx.Observable
import java.util.*

@ModelContainer
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

    @ColumnIgnore
    @JvmField
    var tracks: List<Track>? = null

    @OneToMany(methods = arrayOf(OneToMany.Method.SAVE, OneToMany.Method.DELETE))
    fun getTracks(): List<Track> {
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

        fun get(gameId: Long): Observable<Game> {
            return Observable.create {
                logInfo("[Game] Getting game #${gameId}...")

                if (gameId > 0) {
                    val game = queryDatabase(gameId)

                    if (game != null) {
                        it.onNext(game)
                        it.onCompleted()
                    } else {
                        it.onError(Exception("Couldn't find game."))
                    }
                } else {
                    it.onError(Exception("Bad game ID."))
                }
            }
        }

        fun getFromPlatform(platform: Long): Observable<List<Game>> {
            return Observable.create {
                logInfo("[Game] Reading games list...")

                var games: List<Game>
                val query = SQLite.select().from(Game::class.java)

                // If -2 passed in, return all games. Else, return games for one platform only.
                if (platform != Track.PLATFORM_ALL) {
                    games = query
                            .where(Game_Table.platform.eq(platform))
                            .orderBy(Game_Table.title, true)
                            .queryList()
                } else {
                    games = query
                            .orderBy(Game_Table.title, true)
                            .queryList()
                }

                logVerbose("[Game] Found ${games.size} games.")

                it.onNext(games)
                it.onCompleted()
            }
        }

        fun getFromTrackList(tracks: List<Track>): Observable<HashMap<Long, Game>> {
            return Observable.create {
                logInfo("[Game] Getting games for currently displayed tracks...")
                val startTime = System.currentTimeMillis()

                val games = HashMap<Long, Game>()

                tracks.forEach { track ->
                    logInfo("[Game] Checking for game for track: ${track.title}")

                    track.gameContainer?.toModel()?.id?.let { id ->
                        if (games[id] != null) {
                            logInfo("[Game] Already found.")

                            return@forEach
                        }

                        val game = Game.queryDatabase(id)
                        if (game != null) {
                            games.put(id, game)
                            logInfo("[Game] Added.")
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

        fun get(gameTitle: String?, gamePlatform: Long?, gameMap: HashMap<Long, Game>): Game {
            // Check if this game has already been seen during this scan.
            gameMap.keys.forEach {
                val currentGame = gameMap.get(it)
                if (currentGame?.title == gameTitle && currentGame?.platform == gamePlatform) {
                    currentGame?.let {
                        logVerbose("[Game] Found cached game ${it.title} with id ${it.id}")
                        return it
                    }
                }
            }

            val game = SQLite.select()
                    .from(Game::class.java)
                    .where(Game_Table.title.eq(gameTitle))
                    .and(Game_Table.platform.eq(gamePlatform))
                    .querySingle()

            game?.id?.let {
                gameMap.put(it, game)
                return game
            } ?: let {
                return addToDatabase(gameTitle ?: "Unknown Game", gamePlatform ?: -Track.PLATFORM_UNSUPPORTED)
            }
        }

        fun queryDatabase(id: Long): Game? {
            return SQLite.select()
                    .from(Game::class.java)
                    .where(Game_Table.id.eq(id))
                    .querySingle()
        }

        fun addLocalImage(gameId: Long, artLocal: String) {
            val game = SQLite.update(Game::class.java)
                    .set(Game_Table.artLocal.eq(artLocal))
                    .where(Game_Table.id.eq(gameId))
                    .query()

            if (game != null) {
                logVerbose("[Game] Successfully updated game #$gameId.")
            } else {
                logError("[Game] Failed to update game #$gameId.")
            }
        }

        private fun addToDatabase(gameTitle: String, gamePlatform: Long): Game {
            val game = Game(gameTitle, gamePlatform)
            game.insert()

            return game
        }
    }
}