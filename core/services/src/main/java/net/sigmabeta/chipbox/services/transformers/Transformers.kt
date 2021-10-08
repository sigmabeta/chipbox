package net.sigmabeta.chipbox.services.transformers

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.services.ChipboxPlaybackService


internal fun Game.toMediaItem() = MediaBrowserCompat.MediaItem(
    MediaDescriptionCompat.Builder()
        .setTitle(title)
        .setMediaId(ChipboxPlaybackService.ID_GAMES + "." + id)
        .setIconUri(Uri.parse(photoUrl ?: ""))
        .build(),
    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
)

internal fun Artist.toMediaItem() = MediaBrowserCompat.MediaItem(
    MediaDescriptionCompat.Builder()
        .setTitle(name)
        .setMediaId(ChipboxPlaybackService.ID_ARTISTS + "." + id)
        .setIconUri(Uri.parse(photoUrl ?: ""))
        .build(),
    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
)