package net.sigmabeta.chipbox.services

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.player.director.Director
import javax.inject.Inject

@AndroidEntryPoint
class ChipboxPlaybackService : MediaBrowserServiceCompat() {
    @Inject
    lateinit var browser: LibraryBrowser

    @Inject
    lateinit var director: Director

    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private var mediaSession: MediaSessionCompat? = null

    private var mediaController: MediaControllerCompat? = null

    private lateinit var stateBuilder: PlaybackStateCompat.Builder

    override fun onCreate() {
        super.onCreate()

        val notificationGenerator = NotificationGenerator(this)
        val callback = ChipboxSessionCallback(this, director, notificationGenerator)

        mediaSession = createMediaSession(callback)
        mediaController = MediaControllerCompat(this, mediaSession!!)
        mediaController?.registerCallback(
            object : MediaControllerCompat.Callback() {

            }
        )
    }

    private fun createMediaSession(
        callback: ChipboxSessionCallback
    ) = MediaSessionCompat(baseContext, LOG_TAG).apply {
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
        callback.mediaSession = this

        // Set the session's token so that client activities can communicate with it.
        val mscToken = this.sessionToken
        this@ChipboxPlaybackService.sessionToken = mscToken
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
            result.sendResult(browser.getTopLevelMenuItems())
            return
        }

        result.detach()

        serviceScope.launch {
            browser.browseTo(parentMediaId, result)
        }
    }


    private fun allowBrowsing(clientPackageName: String, clientUid: Int): AccessLevel {
        return AccessLevel.FULL
    }

    companion object {
        private const val ID_ROOT_INFIX = ".media."
        const val ID_ROOT = BuildConfig.LIBRARY_PACKAGE_NAME + ID_ROOT_INFIX

        private const val ID_ROOT_FULL = ID_ROOT + "full"
        private const val ID_ROOT_EMPTY = ID_ROOT + "empty"

        private const val LOG_TAG = "ChipboxPlaybackService"
    }
}
