package net.sigmabeta.chipbox.services

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.services.ChipboxPlaybackService.Companion.ID_ROOT
import net.sigmabeta.chipbox.services.ChipboxPlaybackService.Companion.ID_ROOT_FULL
import net.sigmabeta.chipbox.services.transformers.GRID
import net.sigmabeta.chipbox.services.transformers.LIST
import net.sigmabeta.chipbox.services.transformers.contentStyleExtras
import net.sigmabeta.chipbox.services.transformers.toMediaItem
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Inject

class LibraryBrowser @Inject constructor(
    private val repository: Repository,
    private val hatchet: Hatchet,
) {
    fun getTopLevelMenuItems(): List<MediaItem> {
        return listOf(
            topLevelItemGames(),
            topLevelItemArtists(),
            topLevelItemAllTracks(),
        )
    }

    fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(ID_ROOT_FULL)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Chipbox")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    suspend fun browseTo(parentMediaId: String): List<MediaItem>? = when {
        parentMediaId.startsWith(ID_GAMES) -> browseGames(parentMediaId)
        parentMediaId.startsWith(ID_ARTISTS) -> browseArtists(parentMediaId)
        parentMediaId.startsWith(ID_TRACKS) -> browseTracks(parentMediaId)
        else -> null
    }

    suspend fun getItem(mediaId: String): MediaItem? = when {
        mediaId == ID_ROOT_FULL -> rootItem()
        mediaId == ID_GAMES_TOP -> topLevelItemGames()
        mediaId == ID_ARTISTS_TOP -> topLevelItemArtists()
        mediaId == ID_TRACKS_TOP -> topLevelItemAllTracks()
        mediaId.startsWith(ID_GAMES) -> getGamesItem(mediaId)
        mediaId.startsWith(ID_ARTISTS) -> getArtistsItem(mediaId)
        mediaId.startsWith(ID_TRACKS) -> getTracksItem(mediaId)
        else -> null
    }

    private suspend fun getGamesItem(mediaId: String): MediaItem? {
        val parts = mediaId.removePrefix(ID_GAMES).split('.')
        return when (parts.size) {
            1 -> parts[0].toLongOrNull()?.let { fetchGame(it)?.toMediaItem() }
            2 -> {
                val trackId = parts[1].toLongOrNull() ?: return null
                val track = repository.getTrack(trackId, withGame = true, withArtists = true)
                    ?: return null
                track.toMediaItem(parentId = mediaId.substringBeforeLast('.'))
            }
            else -> null
        }
    }

    private suspend fun getArtistsItem(mediaId: String): MediaItem? {
        val parts = mediaId.removePrefix(ID_ARTISTS).split('.')
        return when (parts.size) {
            1 -> parts[0].toLongOrNull()?.let { fetchArtist(it)?.toMediaItem() }
            2 -> {
                val trackId = parts[1].toLongOrNull() ?: return null
                val track = repository.getTrack(trackId, withGame = true, withArtists = true)
                    ?: return null
                track.toMediaItem(
                    parentId = mediaId.substringBeforeLast('.'),
                    subtitle = track.game?.title ?: UNKNOWN_GAME,
                )
            }
            else -> null
        }
    }

    private suspend fun getTracksItem(mediaId: String): MediaItem? {
        // All-tracks items share the games/artists 3-segment shape (top + trackId) so the
        // IdToCommandParser doesn't need a special case.
        val parts = mediaId.removePrefix(ID_TRACKS).split('.')
        return when {
            parts.size == 2 && parts[0] == ID_TOP -> {
                val trackId = parts[1].toLongOrNull() ?: return null
                val track = repository.getTrack(trackId, withGame = true, withArtists = true)
                    ?: return null
                track.toMediaItem(parentId = ID_TRACKS_TOP)
            }
            else -> null
        }
    }

    private suspend fun fetchGame(id: Long): Game? = repository
        .getGame(id, withTracks = false, withArtists = false)
        .filter { it is Data.Succeeded }
        .map { (it as Data.Succeeded).data }
        .first()

    private suspend fun fetchArtist(id: Long): Artist? = repository
        .getArtist(id, withTracks = false, withGames = false)
        .filter { it is Data.Succeeded }
        .map { (it as Data.Succeeded).data }
        .first()

    private suspend fun browseGames(parentMediaId: String): List<MediaItem>? {
        return when (val id = parentMediaId.substringAfterLast(".")) {
            ID_TOP -> getGamesMenuItems()
            ID_SHUFFLE -> startGamesShuffle()
            else -> {
                val gameId = id.toLongOrNull() ?: return null
                browseToGame(parentMediaId, gameId)
            }
        }
    }

    private suspend fun browseArtists(parentMediaId: String): List<MediaItem>? {
        return when (val id = parentMediaId.substringAfterLast(".")) {
            ID_TOP -> getArtistsMenuItems()
            ID_SHUFFLE -> startArtistsShuffle()
            else -> {
                val artistId = id.toLongOrNull() ?: return null
                browseToArtist(parentMediaId, artistId)
            }
        }
    }

    private suspend fun browseTracks(parentMediaId: String): List<MediaItem>? {
        return when (parentMediaId.substringAfterLast(".")) {
            ID_TOP -> getAllTracksMenuItems()
            else -> null
        }
    }

    private suspend fun browseToGame(parentMediaId: String, gameId: Long) = repository
        .getGame(gameId, true)
        .filter { it is Data.Succeeded }
        .map { it as Data.Succeeded }
        .map { it.data }
        .first()!!
        .let { game -> game.tracks!!.map { it.toMediaItem(parentMediaId, game) } }

    private suspend fun browseToArtist(parentMediaId: String, artistId: Long) = repository
        .getArtist(artistId, true)
        .filter { it is Data.Succeeded }
        .map { it as Data.Succeeded }
        .map { it.data }
        .first()!!
        .tracks!!
        .map { track ->
            if (track.game == null) {
                hatchet.w("Track ${track.id} (${track.title}) has no game attached.")
            }
            track.toMediaItem(parentMediaId, subtitle = track.game?.title ?: UNKNOWN_GAME)
        }

    private fun startGamesShuffle(): List<MediaItem>? {
        TODO("Not yet implemented")
    }

    private fun startArtistsShuffle(): List<MediaItem>? {
        TODO("Not yet implemented")
    }

    private fun topLevelItemGames(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("Games")
            .setDescription("Your Chipbox library, sorted by game title.")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setExtras(contentStyleExtras(browsableChildren = GRID))
            .build()

        return MediaItem.Builder()
            .setMediaId(ID_GAMES_TOP)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun topLevelItemArtists(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("Artists")
            .setDescription("Your Chipbox library, sorted by artist name.")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setExtras(contentStyleExtras(browsableChildren = GRID))
            .build()

        return MediaItem.Builder()
            .setMediaId(ID_ARTISTS_TOP)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun topLevelItemAllTracks(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("All Tracks")
            .setDescription("Your Chipbox library, sorted by track title.")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setExtras(contentStyleExtras(playableChildren = LIST))
            .build()

        return MediaItem.Builder()
            .setMediaId(ID_TRACKS_TOP)
            .setMediaMetadata(metadata)
            .build()
    }

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

    private suspend fun getAllTracksMenuItems() = repository
        .getAllTracks(withGame = true, withArtists = true)
        .filter { it is Data.Succeeded }
        .map { (it as Data.Succeeded).data }
        .first()
        .map { it.toMediaItem(parentId = ID_TRACKS_TOP) }

    companion object {
        private const val UNKNOWN_GAME = "Unknown Game"

        private const val ID_TOP = "top"
        private const val ID_SHUFFLE = "shuffle"

        const val COMMAND_GAMES = "games"
        const val COMMAND_ARTISTS = "artists"
        const val COMMAND_TRACKS = "tracks"

        const val ID_GAMES = ID_ROOT + COMMAND_GAMES + "."
        const val ID_ARTISTS = ID_ROOT + COMMAND_ARTISTS + "."
        const val ID_TRACKS = ID_ROOT + COMMAND_TRACKS + "."

        const val ID_GAMES_TOP = ID_GAMES + ID_TOP
        const val ID_ARTISTS_TOP = ID_ARTISTS + ID_TOP
        const val ID_TRACKS_TOP = ID_TRACKS + ID_TOP

        const val ID_GAMES_SHUFFLE = ID_GAMES + ID_SHUFFLE
        const val ID_ARTISTS_SHUFFLE = ID_ARTISTS + ID_SHUFFLE
    }
}
