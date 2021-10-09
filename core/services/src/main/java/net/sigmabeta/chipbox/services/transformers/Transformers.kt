package net.sigmabeta.chipbox.services.transformers

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.services.ChipboxPlaybackService
import net.sigmabeta.chipbox.services.LibraryBrowser.Companion.ID_ARTISTS
import net.sigmabeta.chipbox.services.LibraryBrowser.Companion.ID_GAMES


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