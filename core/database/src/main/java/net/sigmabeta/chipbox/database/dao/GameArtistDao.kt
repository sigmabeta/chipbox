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
    fun insertAll(gameArtistJoins: List<GameArtistJoin>)

    @Query(
        """ 
            SELECT * FROM artist INNER JOIN game_artist_join 
            ON artist.id=game_artist_join.artistId
            WHERE game_artist_join.gameId=:gameId
            ORDER BY name
            COLLATE NOCASE
            """
    )
    fun getArtistsForGame(gameId: Long): List<ArtistEntity>

    @Query(
        """ 
            SELECT * FROM game INNER JOIN game_artist_join 
            ON game.id=game_artist_join.gameId
            WHERE game_artist_join.artistId=:artistId
            ORDER BY title
            COLLATE NOCASE
            """
    )
    fun getGamesForArtistSync(artistId: Long): List<GameEntity>

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
    fun nukeTable()
}
