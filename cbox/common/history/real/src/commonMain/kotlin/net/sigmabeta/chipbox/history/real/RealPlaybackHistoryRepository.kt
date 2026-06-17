package net.sigmabeta.chipbox.history.real

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import net.sigmabeta.chipbox.entities.SongPlayEntity
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.history.dao.ArtistPlayCountDao
import net.sigmabeta.chipbox.history.dao.GamePlayCountDao
import net.sigmabeta.chipbox.history.dao.SongPlayCountDao
import net.sigmabeta.chipbox.history.dao.SongPlayDao
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.sage.logging.Hatchet

/**
 * Production [PlaybackHistoryRepository]. [recordPlay] logs the play and fans the increment out to
 * the song, its game, and each credited artist, all stamped with one wall-clock timestamp so the
 * play and its counter bumps agree. Room dispatches the suspend DAO calls off the caller thread, so
 * no explicit dispatcher hop is needed here.
 */
class RealPlaybackHistoryRepository(
    private val songPlayDao: SongPlayDao,
    private val songPlayCountDao: SongPlayCountDao,
    private val gamePlayCountDao: GamePlayCountDao,
    private val artistPlayCountDao: ArtistPlayCountDao,
    private val hatchet: Hatchet,
) : PlaybackHistoryRepository {

    @OptIn(ExperimentalTime::class)
    override suspend fun recordPlay(track: Track) {
        val now = Clock.System.now().toEpochMilliseconds()

        songPlayDao.insert(SongPlayEntity(trackId = track.id, timeMs = now))
        songPlayCountDao.increment(track.id, now)

        // gameId is always populated on a Track (non-null FK in the library); 0 means "unset".
        if (track.gameId != 0L) {
            gamePlayCountDao.increment(track.gameId, now)
        }

        track.artists?.forEach { artist ->
            artistPlayCountDao.increment(artist.id, now)
        }

        hatchet.d("Recorded play: track=${track.id} game=${track.gameId} artists=${track.artists?.size ?: 0}")
    }

    override suspend fun clearHistory() {
        songPlayDao.nukeTable()
        songPlayCountDao.nukeTable()
        gamePlayCountDao.nukeTable()
        artistPlayCountDao.nukeTable()
        hatchet.i("Cleared playback history.")
    }
}
