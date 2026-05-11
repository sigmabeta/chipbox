package net.sigmabeta.chipbox.services.transformers

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import net.sigmabeta.chipbox.artwork.ArtworkUris
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.services.LibraryBrowser.Companion.ID_ARTISTS
import net.sigmabeta.chipbox.services.LibraryBrowser.Companion.ID_GAMES

internal fun Game.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setIsBrowsable(true)
        .setIsPlayable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
        .apply { iconUri()?.let { setArtworkUri(it) } }
        .build()

    return MediaItem.Builder()
        .setMediaId(ID_GAMES + id)
        .setMediaMetadata(metadata)
        .build()
}

internal fun Artist.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(name)
        .setIsBrowsable(true)
        .setIsPlayable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
        .apply { iconUri()?.let { setArtworkUri(it) } }
        .build()

    return MediaItem.Builder()
        .setMediaId(ID_ARTISTS + id)
        .setMediaMetadata(metadata)
        .build()
}

internal fun Track.toMediaItem(
    parentId: String,
    game: Game? = this.game,
): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setSubtitle(getArtistText())
        .setArtist(getArtistText())
        .setAlbumTitle(game?.title)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .apply { game?.iconUri()?.let { setArtworkUri(it) } }
        .build()

    return MediaItem.Builder()
        .setMediaId("$parentId.$id")
        .setMediaMetadata(metadata)
        .build()
}

internal fun Track.toMediaMetadata(): MediaMetadata {
    val builder = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(getArtistText())
        .setAlbumTitle(game?.title)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
    game?.iconUri()?.let { builder.setArtworkUri(it) }
    return builder.build()
}

private fun Game.iconUri() = if (photoUrl != null) ArtworkUris.forGame(id) else null

private fun Artist.iconUri() = if (photoUrl != null) ArtworkUris.forArtist(id) else null

private fun Track.getArtistText(): String {
    return when (artists?.size) {
        null, 0 -> "Unknown Artist"
        1 -> artists!!.first().name
        2, 3 -> artists!!.joinToString(", ") { it.name }
        else -> "Various Artists"
    }
}
