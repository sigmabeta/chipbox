package net.sigmabeta.chipbox.model.domain

import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.sql.language.SQLite
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import rx.Observable
import java.util.*

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

                for (track in tracks) {
                    val gameId = track.gameId ?: -1

                    if (games[gameId] != null) {
                        continue
                    }

                    if (gameId > 0) {
                        val game = Game.queryDatabase(gameId)
                        if (game != null) {
                            games.put(gameId, game)
                        } else {
                            it.onError(Exception("Couldn't find game."))
                            return@create
                        }
                    } else {
                        it.onError(Exception("Bad game ID: $gameId"))
                        return@create
                    }
                }

                logVerbose("[Game] Game map size: ${games.size}")

                val endTime = System.currentTimeMillis()
                val scanDuration = (endTime - startTime) / 1000.0f

                logInfo("[Game] Found games in ${scanDuration} seconds.")

                it.onNext(games)
                it.onCompleted()
            }
        }

        fun getId(gameTitle: String?, gamePlatform: Long?, gameMap: HashMap<Long, Game>): Long {
            // Check if this game has already been seen during this scan.
            gameMap.keys.forEach {
                val currentGame = gameMap.get(it)
                if (currentGame?.title == gameTitle && currentGame?.platform == gamePlatform) {
                    currentGame?.id?.let {
                        logVerbose("[Game] Found cached game $gameTitle with id ${it}")
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
                return it
            } ?: let {
                val newGame = addToDatabase(gameTitle ?: "Unknown Game", gamePlatform ?: -Track.PLATFORM_UNSUPPORTED)
                newGame.id?.let {
                    return it
                }
            }
            logError("[Game] Unable to find game ID.")
            return -1L
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