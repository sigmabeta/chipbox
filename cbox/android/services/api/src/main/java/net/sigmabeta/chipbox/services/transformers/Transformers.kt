package net.sigmabeta.chipbox.services.transformers

import android.media.MediaMetadata
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_APP_ERROR
import net.sigmabeta.chipbox.artwork.ArtworkUris
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.services.LibraryBrowser.Companion.ID_ARTISTS
import net.sigmabeta.chipbox.services.LibraryBrowser.Companion.ID_GAMES
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet

private val hatchet: Hatchet = BluntHatchet()

internal fun Game.toMediaItem() = MediaBrowserCompat.MediaItem(
    MediaDescriptionCompat.Builder()
        .setTitle(title)
        .setMediaId(ID_GAMES + id)
        .apply { iconUri()?.let { setIconUri(it) } }
        .build(),
    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
)

internal fun Artist.toMediaItem() = MediaBrowserCompat.MediaItem(
    MediaDescriptionCompat.Builder()
        .setTitle(name)
        .setMediaId(ID_ARTISTS + id)
        .apply { iconUri()?.let { setIconUri(it) } }
        .build(),
    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
)

internal fun Track.toMediaItem(
    parentId: String,
    game: Game? = this.game,
) = MediaBrowserCompat.MediaItem(
    MediaDescriptionCompat.Builder()
        .setTitle(title)
        .setSubtitle(getArtistText())
        .setMediaId("$parentId.$id")
        .apply { game?.iconUri()?.let { setIconUri(it) } }
        .build(),
    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
)

private fun Game.iconUri() = if (photoUrl != null) ArtworkUris.forGame(id) else null

private fun Artist.iconUri() = if (photoUrl != null) ArtworkUris.forArtist(id) else null

internal fun Track.toMetadataBuilder(): MediaMetadataCompat.Builder {
    val builder = MediaMetadataCompat.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .putString(MediaMetadata.METADATA_KEY_ALBUM, game?.title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, getArtistText())
    val artUri = game?.iconUri()?.toString()
    if (artUri != null) {
        builder.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, artUri)
        builder.putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, artUri)
    }
    return builder
}


internal fun ChipboxPlaybackState.toAndroidXPlaybackState(): PlaybackStateCompat {
    val builder = PlaybackStateCompat.Builder()

    if (state == PlayerState.ERROR) {
        builder.setErrorMessage(ERROR_CODE_APP_ERROR, errorMessage)
    }

    return builder
        .setState(state.toAndroidXPlayerState(), position, playbackSpeed)
        .setActions(availableActions())
        .setBufferedPosition(bufferPosition)
        .build()
}

private fun PlayerState.toAndroidXPlayerState() = when (this) {
    PlayerState.IDLE -> PlaybackStateCompat.STATE_NONE
    PlayerState.STOPPED -> PlaybackStateCompat.STATE_STOPPED
    PlayerState.BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
    PlayerState.PRELOADING -> PlaybackStateCompat.STATE_PLAYING
    PlayerState.PLAYING -> PlaybackStateCompat.STATE_PLAYING
    PlayerState.FAST_FORWARDING -> PlaybackStateCompat.STATE_FAST_FORWARDING
    PlayerState.REWINDING -> PlaybackStateCompat.STATE_REWINDING
    PlayerState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
    PlayerState.ENDING -> PlaybackStateCompat.STATE_STOPPED
    PlayerState.ERROR -> PlaybackStateCompat.STATE_ERROR
}

private fun ChipboxPlaybackState.availableActions(): Long {
    var actions = 0L

    hatchet.w("Generating available actions for state: $state")
    actions = when (state) {
        PlayerState.PLAYING, PlayerState.PRELOADING, PlayerState.BUFFERING -> PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PAUSE
        PlayerState.PAUSED -> PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY
        PlayerState.STOPPED -> PlaybackStateCompat.ACTION_PLAY
        else -> return actions
    }

    if (skipForwardAllowed) {
        actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
    }

    actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

    return actions
}

private fun Track.getArtistText(): String {
    return when (artists?.size) {
        null, 0 -> "Unknown Artist"
        1 -> artists!!.first().name
        2, 3 -> artists!!.joinToString(", ") { it.name }
        else -> "Various Artists" // TODO String resources
    }
}
