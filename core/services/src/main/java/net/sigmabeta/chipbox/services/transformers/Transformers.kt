package net.sigmabeta.chipbox.services.transformers

import android.media.MediaMetadata
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_APP_ERROR
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.services.LibraryBrowser.Companion.ID_ARTISTS
import net.sigmabeta.chipbox.services.LibraryBrowser.Companion.ID_GAMES
import timber.log.Timber

internal fun Game.toMediaItem() = MediaBrowserCompat.MediaItem(
    MediaDescriptionCompat.Builder()
        .setTitle(title)
        .setMediaId(ID_GAMES + id)
        .setIconUri(Uri.parse(photoUrl ?: ""))
        .build(),
    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
)

internal fun Artist.toMediaItem() = MediaBrowserCompat.MediaItem(
    MediaDescriptionCompat.Builder()
        .setTitle(name)
        .setMediaId(ID_ARTISTS + id)
        .setIconUri(Uri.parse(photoUrl ?: ""))
        .build(),
    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
)

internal fun Track.toMediaItem(parentId: String) = MediaBrowserCompat.MediaItem(
    MediaDescriptionCompat.Builder()
        .setTitle(title)
        .setMediaId("$parentId.$id")
        .setIconUri(Uri.parse(game?.photoUrl ?: ""))
        .build(),
    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
)

internal fun Track.toMetadataBuilder(): MediaMetadataCompat.Builder {
    return MediaMetadataCompat.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .putString(MediaMetadata.METADATA_KEY_ALBUM, game?.title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, getArtistText())
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

    Timber.w("Generating available actions for state: $state")
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
