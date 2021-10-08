package net.sigmabeta.chipbox.services

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.services.transformers.toMediaItem
import javax.inject.Inject

@AndroidEntryPoint
class ChipboxPlaybackService : MediaBrowserServiceCompat() {
    @Inject
    lateinit var callback: ChipboxServiceCallback

    @Inject
    lateinit var repository: Repository

    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private var mediaSession: MediaSessionCompat? = null
    private lateinit var stateBuilder: PlaybackStateCompat.Builder

    override fun onCreate() {
        super.onCreate()
        callback.service = this

        // Create a MediaSessionCompat
        mediaSession = MediaSessionCompat(baseContext, LOG_TAG).apply {
            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            stateBuilder = PlaybackStateCompat
                .Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE
                )

            val state = stateBuilder.build()
            setPlaybackState(state)

            // MySessionCallback() has methods that handle callbacks from a media controller
            setCallback(callback)

            // Set the session's token so that client activities can communicate with it.
            val mscToken = this.sessionToken
            this@ChipboxPlaybackService.setSessionToken(mscToken)
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ) = when (allowBrowsing(clientPackageName, clientUid)) {
        AccessLevel.FULL -> BrowserRoot(ID_ROOT_FULL, null)
        AccessLevel.NO_BROWSE -> BrowserRoot(ID_ROOT_EMPTY, null)
        AccessLevel.NONE -> null
    }

    override fun onLoadChildren(
        parentMediaId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        //  Browsing not allowed
        if (ID_ROOT_EMPTY == parentMediaId) {
            result.sendResult(null)
            return
        }

        if (parentMediaId == ID_ROOT_FULL) {
            result.sendResult(getTopLevelMenuItems())
            return
        }

        result.detach()

        serviceScope.launch {
            val mediaItems = when (parentMediaId) {
                ID_GAMES -> getGamesMenuItems()
                ID_ARTISTS -> getArtistsMenuItems()
                else -> {
                    result.sendError(Bundle.EMPTY)
                    return@launch
                }
            }

            result.sendResult(mediaItems)
        }
    }

    private fun getTopLevelMenuItems(): List<MediaBrowserCompat.MediaItem> {
        return listOf(
            topLevelItemGames(),
            topLevelItemArtists()
        )
    }

    private fun topLevelItemGames() = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(ID_GAMES)
            .setTitle("Games")
            .setDescription("Your Chipbox library, sorted by game title.")
            .build(),
        FLAG_BROWSABLE
    )

    private fun topLevelItemArtists() = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(ID_ARTISTS)
            .setTitle("Artists")
            .setDescription("Your Chipbox library, sorted by artist name.")
            .build(),
        FLAG_BROWSABLE
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


    private fun allowBrowsing(clientPackageName: String, clientUid: Int): AccessLevel {
        return AccessLevel.FULL
    }

    companion object {
        private const val ID_ROOT_INFIX = ".media.id.root."
        private const val ID_ROOT = BuildConfig.LIBRARY_PACKAGE_NAME + ID_ROOT_INFIX

        private const val ID_ROOT_FULL = ID_ROOT + "full"
        private const val ID_ROOT_EMPTY = ID_ROOT + "empty"

        const val ID_GAMES = ID_ROOT + "games"
        const val ID_ARTISTS = ID_ROOT + "artists"

        private const val LOG_TAG = "ChipboxPlaybackService"
    }
}
