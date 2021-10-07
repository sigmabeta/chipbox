package net.sigmabeta.chipbox.services

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChipboxPlaybackService : MediaBrowserServiceCompat() {
    @Inject
    lateinit var callback: ChipboxServiceCallback

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

        val mediaItems = when (parentMediaId) {
            ID_ROOT_FULL -> getTopLevelMenuItems()
            ID_ROOT_GAMES -> getGamesMenuItems()
            else -> {
                result.sendError(Bundle.EMPTY)
                return
            }
        }

        result.sendResult(mediaItems)
    }

    private fun getTopLevelMenuItems(): List<MediaBrowserCompat.MediaItem> {
        TODO("Not yet implemented")
    }

    private fun getGamesMenuItems(): List<MediaBrowserCompat.MediaItem>? {
        TODO("Not yet implemented")
    }

    private fun allowBrowsing(clientPackageName: String, clientUid: Int): AccessLevel {
        return AccessLevel.FULL
    }


    companion object {
        private const val ID_ROOT_INFIX = ".media.id.root."
        private const val ID_ROOT = BuildConfig.LIBRARY_PACKAGE_NAME + ID_ROOT_INFIX

        private const val ID_ROOT_FULL = ID_ROOT + "full"
        private const val ID_ROOT_EMPTY = ID_ROOT + "empty"

        private const val ID_ROOT_GAMES = ID_ROOT + "games"

        private const val LOG_TAG = "ChipboxPlaybackService"
    }
}