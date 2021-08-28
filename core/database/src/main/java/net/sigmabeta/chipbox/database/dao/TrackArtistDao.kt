package net.sigmabeta.chipbox.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.TrackEntity
import net.sigmabeta.chipbox.entities.joins.TrackArtistJoin

@Dao
interface TrackArtistDao {
    @Insert
    fun insertAll(trackArtistJoins: List<TrackArtistJoin>)

    @Query(
        """ 
            SELECT * FROM artist INNER JOIN track_artist_join 
            ON artist.id=track_artist_join.artistId
            WHERE track_artist_join.trackId=:trackId
            ORDER BY name
            COLLATE NOCASE
            """
    )
    fun getArtistsForTrack(trackId: Long): List<ArtistEntity>

    @Query(
        """ 
            SELECT * FROM artist INNER JOIN track_artist_join 
            ON artist.id=track_artist_join.artistId
            WHERE track_artist_join.trackId=:trackId
            ORDER BY name
            COLLATE NOCASE
            """
    )
    fun getArtistsForTrackSync(trackId: Long): List<ArtistEntity>

    @Query(
        """ 
            SELECT * FROM track INNER JOIN track_artist_join 
            ON track.id=track_artist_join.trackId
            WHERE track_artist_join.artistId=:artistId
            COLLATE NOCASE
            """
    )
    fun getTracksForArtistSync(artistId: Long): List<TrackEntity>

    @Query(
        """ 
            SELECT * FROM track INNER JOIN track_artist_join 
            ON track.id=track_artist_join.trackId
            WHERE track_artist_join.artistId=:artistId
            ORDER BY title
            COLLATE NOCASE
            """
    )
    fun getTracksForArtist(artistId: Long): Flow<List<TrackEntity>>

    @Query("DELETE FROM track_artist_join")
    fun nukeTable()
}
