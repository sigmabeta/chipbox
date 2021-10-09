package net.sigmabeta.chipbox.services

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.services.ChipboxPlaybackService.Companion.ID_ROOT
import net.sigmabeta.chipbox.services.transformers.toMediaItem
import javax.inject.Inject

class LibraryBrowser @Inject constructor(
    private val repository: Repository
) {
    fun getTopLevelMenuItems(): List<MediaBrowserCompat.MediaItem> {
        return listOf(
            topLevelItemGames(),
            topLevelItemArtists()
        )
    }

    suspend fun browseTo(parentMediaId: String, result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = when {
            parentMediaId.startsWith(ID_GAMES) -> browseGames(parentMediaId)
            parentMediaId.startsWith(ID_ARTISTS) -> browseArtists(parentMediaId)
            else -> null
        }

        result.sendResult(mediaItems)
    }

    private suspend fun browseGames(parentMediaId: String): List<MediaBrowserCompat.MediaItem>? {
        return when (val id = parentMediaId.substringAfterLast(".")) {
            ID_TOP -> getGamesMenuItems()
            ID_SHUFFLE -> startGamesShuffle()
            else -> {
                val gameId = id.toLongOrNull() ?: return null
                browseToGame(parentMediaId, gameId)
            }
        }
    }

    private suspend fun browseArtists(parentMediaId: String): List<MediaBrowserCompat.MediaItem>? {
        return when (val id = parentMediaId.substringAfterLast(".")) {
            ID_TOP -> getArtistsMenuItems()
            ID_SHUFFLE -> startArtistsShuffle()
            else -> {
                val artistId = id.toLongOrNull() ?: return null
                browseToArtist(parentMediaId, artistId)
            }
        }
    }

    private suspend fun browseToGame(parentMediaId: String, gameId: Long) = repository
        .getGame(gameId, true)
        .filter { it is Data.Succeeded }
        .map { it as Data.Succeeded }
        .map { it.data }
        .first()!!
        .tracks!!
        .map { it.toMediaItem(parentMediaId) }

    private suspend fun browseToArtist(parentMediaId: String, artistId: Long) = repository
        .getArtist(artistId, true)
        .filter { it is Data.Succeeded }
        .map { it as Data.Succeeded }
        .map { it.data }
        .first()!!
        .tracks!!
        .map { it.toMediaItem(parentMediaId) }

    private fun startGamesShuffle(): List<MediaBrowserCompat.MediaItem>? {
        TODO("Not yet implemented")
    }

    private fun startArtistsShuffle(): List<MediaBrowserCompat.MediaItem>? {
        TODO("Not yet implemented")
    }

    private fun topLevelItemGames() = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(ID_GAMES_TOP)
            .setTitle("Games")
            .setDescription("Your Chipbox library, sorted by game title.")
            .build(),
        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
    )

    private fun topLevelItemArtists() = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(ID_ARTISTS_TOP)
            .setTitle("Artists")
            .setDescription("Your Chipbox library, sorted by artist name.")
            .build(),
        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
    )

    private suspend fun getGamesMenuItems() = repository
        .getAllGames(false, false)
        .filter { it is Data.Succeeded }
        .map { it as Data.Succeeded }
        .map { it.data }
        .first()
        .map { it.toMediaItem() }

    private suspend fun getArtistsMenuItems() = repository
        .getAllArtists(false, false)
        .filter { it is Data.Succeeded }
        .map { it as Data.Succeeded }
        .map { it.data }
        .first()
        .map { it.toMediaItem() }

    companion object {
        private const val ID_TOP = "top"
        private const val ID_SHUFFLE = "shuffle"

        const val COMMAND_GAMES = "games"
        const val COMMAND_ARTISTS = "artists"

        const val ID_GAMES = ID_ROOT + COMMAND_GAMES + "."
        const val ID_ARTISTS = ID_ROOT + COMMAND_ARTISTS + "."

        const val ID_GAMES_TOP = ID_GAMES + ID_TOP
        const val ID_ARTISTS_TOP = ID_ARTISTS + ID_TOP

        const val ID_GAMES_SHUFFLE = ID_GAMES + ID_SHUFFLE
        const val ID_ARTISTS_SHUFFLE = ID_ARTISTS + ID_SHUFFLE
    }
}