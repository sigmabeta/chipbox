package net.sigmabeta.chipbox.services

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.player.director.Director
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ChipboxPlaybackService : MediaBrowserServiceCompat() {
    @Inject
    lateinit var browser: LibraryBrowser

    @Inject
    lateinit var director: Director

    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private var mediaSession: MediaSessionCompat? = null

//    private var mediaController: MediaControllerCompat? = null

    override fun onCreate() {
        super.onCreate()

        Timber.i("Starting service...")

        val notificationGenerator = NotificationGenerator(this)
        val systemNotifService = NotificationManagerCompat.from(this)

        val callback = ChipboxSessionCallback(
            this,
            serviceScope,
            director,
            notificationGenerator,
            systemNotifService
        )

        mediaSession = createMediaSession(callback)
//        mediaController = MediaControllerCompat(this, mediaSession!!)
//        mediaController?.registerCallback(
//            object : MediaControllerCompat.Callback() {
//                override fun onSessionDestroyed() {
//                    Timber.e("Session destroyed in Activity.")
//                     maybe schedule a reconnection using a new MediaBrowser instance
//                }
//            }
//        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("Destroying service...")
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        Timber.v("onGetRoot for $clientPackageName")
        return when (allowBrowsing(clientPackageName, clientUid)) {
            AccessLevel.FULL -> BrowserRoot(ID_ROOT_FULL, null)
            AccessLevel.NO_BROWSE -> BrowserRoot(ID_ROOT_EMPTY, null)
            AccessLevel.NONE -> null
        }
    }

    override fun onLoadChildren(
        parentMediaId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
//        Timber.v("onLoadChildren for $parentMediaId")

        //  Browsing not allowed
        if (ID_ROOT_EMPTY == parentMediaId) {
            Timber.w("App not permitted to browse library.")
            result.sendResult(null)
            return
        }

        if (parentMediaId == ID_ROOT_FULL) {
            val topLevelMenuItems = browser.getTopLevelMenuItems()
            Timber.d("Sending top level menu items: ${topLevelMenuItems}")
            result.sendResult(topLevelMenuItems)
            return
        }

        result.detach()

        serviceScope.launch {
            browser.browseTo(parentMediaId, result)
        }
    }

    private fun createMediaSession(
        callback: ChipboxSessionCallback
    ) = MediaSessionCompat(baseContext, LOG_TAG).apply {
        // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
        val state = PlaybackStateCompat
            .Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE
            ).build()

        setPlaybackState(state)

        setCallback(callback)
        callback.mediaSession = this

        // Set the session's token so that client activities can communicate with it.
        val mscToken = sessionToken
        Timber.v("Creating session with Token: $sessionToken active: $isActive")

        this@ChipboxPlaybackService.sessionToken = mscToken
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
