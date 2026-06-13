package net.sigmabeta.chipbox.services.api.transformers

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import net.sigmabeta.chipbox.artworkprovider.api.ArtworkUris
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.services.api.LibraryBrowser.Companion.ID_ARTISTS
import net.sigmabeta.chipbox.services.api.LibraryBrowser.Companion.ID_GAMES
import net.sigmabeta.chipbox.services.api.LibraryBrowser.Companion.ID_PLATFORMS

internal fun Game.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setIsBrowsable(true)
        .setIsPlayable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
        .setExtras(contentStyleExtras(playableChildren = LIST))
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
        .setExtras(contentStyleExtras(playableChildren = LIST))
        .apply { iconUri()?.let { setArtworkUri(it) } }
        .build()

    return MediaItem.Builder()
        .setMediaId(ID_ARTISTS + id)
        .setMediaMetadata(metadata)
        .build()
}

internal fun Platform.toMediaItem(title: String): MediaItem {
    // A platform folder drills into its games — the same browsable game items as the Games
    // branch, so it reuses ID_GAMES for those children and wants their GRID display style.
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setIsBrowsable(true)
        .setIsPlayable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        .setExtras(contentStyleExtras(browsableChildren = GRID))
        .build()

    return MediaItem.Builder()
        .setMediaId(ID_PLATFORMS + name)
        .setMediaMetadata(metadata)
        .build()
}

internal fun Track.toMediaItem(
    parentId: String,
    game: Game? = this.game,
    subtitle: String? = getArtistText(),
): MediaItem {
    // Mirror subtitle into the artist field — different MediaBrowser renderers pick
    // different ones, so keep them consistent. Default subtitle is artist text, so
    // game-context browsing is unchanged; artist-context browsing surfaces the game.
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setArtist(subtitle)
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
    // The now-playing session surfaces the game in the artist slot — chiptune "artist" is
    // often unknown/various, whereas the game is the meaningful subtitle for a track.
    val builder = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(game?.title ?: UNKNOWN_GAME)
        .setAlbumTitle(game?.title)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
    game?.iconUri()?.let { builder.setArtworkUri(it) }
    return builder.build()
}

internal const val LIST = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
internal const val GRID = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM

internal fun contentStyleExtras(
    browsableChildren: Int? = null,
    playableChildren: Int? = null,
): Bundle = Bundle().apply {
    browsableChildren?.let {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, it)
    }
    playableChildren?.let {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, it)
    }
}

private fun Game.iconUri() = if (photoUrl != null) ArtworkUris.forGame(id) else null

private fun Artist.iconUri() = if (photoUrl != null) ArtworkUris.forArtist(id) else null

private const val UNKNOWN_GAME = "Unknown Game"

private const val MAX_ARTISTS_TO_LIST = 3

private fun Track.getArtistText(): String = when (artists?.size) {
        null, 0 -> "Unknown Artist"
        1 -> artists!!.first().name
        2, MAX_ARTISTS_TO_LIST -> artists!!.joinToString(", ") { it.name }
        else -> "Various Artists"
    }
