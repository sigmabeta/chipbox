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
    suspend fun insertAll(trackArtistJoins: List<TrackArtistJoin>)

    // Reconciliation: clear a track's artist links before rebuilding them on rescan.
    @Query("DELETE FROM track_artist_join WHERE trackId IN (:trackIds)")
    suspend fun deleteForTracks(trackIds: List<Long>)

    @Query(
        """
            SELECT * FROM artist INNER JOIN track_artist_join
            ON artist.id=track_artist_join.artistId
            WHERE track_artist_join.trackId=:trackId
            ORDER BY name
            COLLATE NOCASE
            """
    )
    suspend fun getArtistsForTrack(trackId: Long): List<ArtistEntity>

    @Query(
        """
            SELECT * FROM artist INNER JOIN track_artist_join
            ON artist.id=track_artist_join.artistId
            WHERE track_artist_join.trackId=:trackId
            ORDER BY name
            COLLATE NOCASE
            """
    )
    suspend fun getArtistsForTrackSync(trackId: Long): List<ArtistEntity>

    // Canonical order for an artist's tracks: A–Z by game title, then track number within a game.
    // Sorting here (joining game for its title) makes the order independent of whether the caller
    // hydrates each track's Game — so the artist-detail screen (withGame=true, for the row caption)
    // and the playback Director (withGame=false, ids only) get identical order. SELECT track.* so the
    // game join's id/title columns don't collide with track's.
    @Query(
        """
            SELECT track.* FROM track
            INNER JOIN track_artist_join ON track.id=track_artist_join.trackId
            INNER JOIN game ON track.game_id=game.id
            WHERE track_artist_join.artistId=:artistId
            ORDER BY game.title COLLATE NOCASE, track.trackNumber
            """
    )
    suspend fun getTracksForArtistSync(artistId: Long): List<TrackEntity>

    @Query(
        """
            SELECT track.* FROM track
            INNER JOIN track_artist_join ON track.id=track_artist_join.trackId
            INNER JOIN game ON track.game_id=game.id
            WHERE track_artist_join.artistId=:artistId
            ORDER BY game.title COLLATE NOCASE, track.trackNumber
            """
    )
    fun getTracksForArtist(artistId: Long): Flow<List<TrackEntity>>

    @Query("DELETE FROM track_artist_join")
    suspend fun nukeTable()
}
