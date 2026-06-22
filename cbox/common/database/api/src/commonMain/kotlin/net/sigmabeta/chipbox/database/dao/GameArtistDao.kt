package net.sigmabeta.chipbox.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.joins.GameArtistJoin

@Dao
interface GameArtistDao {
    @Insert
    suspend fun insertAll(gameArtistJoins: List<GameArtistJoin>)

    // Reconciliation: clear a game's artist links before rebuilding them on rescan.
    @Query("DELETE FROM game_artist_join WHERE gameId = :gameId")
    suspend fun deleteForGame(gameId: Long)

    // Artist ids for a game (by name) and game ids for an artist (by title), in display order.
    // Hydration resolves each id through the artist-by-id / game-by-id caches, so a shared
    // artist or game is read from storage once and reused. Each joins the target table only to
    // order the ids.
    @Query(
        """
            SELECT game_artist_join.artistId FROM game_artist_join
            INNER JOIN artist ON artist.id=game_artist_join.artistId
            WHERE game_artist_join.gameId=:gameId
            ORDER BY artist.name COLLATE NOCASE
            """
    )
    suspend fun getArtistIdsForGame(gameId: Long): List<Long>

    @Query(
        """
            SELECT game_artist_join.gameId FROM game_artist_join
            INNER JOIN game ON game.id=game_artist_join.gameId
            WHERE game_artist_join.artistId=:artistId
            ORDER BY game.title COLLATE NOCASE
            """
    )
    suspend fun getGameIdsForArtist(artistId: Long): List<Long>

    @Query(
        """ 
            SELECT * FROM artist INNER JOIN game_artist_join 
            ON artist.id=game_artist_join.artistId
            WHERE game_artist_join.gameId=:gameId
            ORDER BY name
            COLLATE NOCASE
            """
    )
    fun getArtistsForGame(gameId: Long): Flow<List<ArtistEntity>>

    @Query(
        """ 
            SELECT * FROM artist INNER JOIN game_artist_join 
            ON artist.id=game_artist_join.artistId
            WHERE game_artist_join.gameId=:gameId
            ORDER BY name
            COLLATE NOCASE
            """
    )
    suspend fun getArtistsForGameSync(gameId: Long): List<ArtistEntity>

    @Query(
        """ 
            SELECT * FROM game INNER JOIN game_artist_join 
            ON game.id=game_artist_join.gameId
            WHERE game_artist_join.artistId=:artistId
            ORDER BY title
            COLLATE NOCASE
            """
    )
    suspend fun getGamesForArtistSync(artistId: Long): List<GameEntity>

    @Query(
        """ 
            SELECT * FROM game INNER JOIN game_artist_join 
            ON game.id=game_artist_join.gameId
            WHERE game_artist_join.artistId=:artistId
            ORDER BY title
            COLLATE NOCASE
            """
    )
    fun getGamesForArtist(artistId: Long): Flow<List<GameEntity>>

    @Query("DELETE FROM game_artist_join")
    suspend fun nukeTable()
}
