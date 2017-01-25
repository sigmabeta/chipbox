package net.sigmabeta.chipbox.backend

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.session.PlaybackState
import android.os.SystemClock
import android.support.v4.app.NotificationCompat.Action
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.app.NotificationCompat
import android.view.KeyEvent
import io.realm.Realm
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.repository.Repository
import net.sigmabeta.chipbox.util.loadBitmapLowQuality
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logVerbose
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

class MediaNotificationManager(val playerService: PlayerService, val repository: Repository?) : BroadcastReceiver() {
    val prevIntent = PendingIntent.getBroadcast(playerService, REQUEST_CODE,
            Intent(ACTION_PREV).setPackage(playerService.packageName), PendingIntent.FLAG_CANCEL_CURRENT)
    val pauseIntent = PendingIntent.getBroadcast(playerService, REQUEST_CODE,
            Intent(ACTION_PAUSE).setPackage(playerService.packageName), PendingIntent.FLAG_CANCEL_CURRENT)
    val playIntent = PendingIntent.getBroadcast(playerService, REQUEST_CODE,
            Intent(ACTION_PLAY).setPackage(playerService.packageName), PendingIntent.FLAG_CANCEL_CURRENT)
    val stopIntent = PendingIntent.getBroadcast(playerService, REQUEST_CODE,
            Intent(ACTION_STOP).setPackage(playerService.packageName), PendingIntent.FLAG_CANCEL_CURRENT)
    val nextIntent = PendingIntent.getBroadcast(playerService, REQUEST_CODE,
            Intent(ACTION_NEXT).setPackage(playerService.packageName), PendingIntent.FLAG_CANCEL_CURRENT)

    val notificationService = playerService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    var mediaController: MediaControllerCompat? = null
    var transportControls: MediaControllerCompat.TransportControls? = null
    var sessionToken: MediaSessionCompat.Token? = null

    var mediaMetadata: MediaMetadataCompat? = null
    var playbackState: PlaybackStateCompat? = null

    var playingTrack: Track? = null
    var playingGame: Game? = null

    var playingGameArtPath: String? = null
    var playingGameArtBitmap: Bitmap? = null

    var notified = false

    var subscription: Subscription? = null

    init {
        updateSessionToken()
        notificationService.cancelAll()
    }

    fun subscribeToUpdates() {
        val player = playerService.player

        if (player != null) {
            subscription = player.updater.asObservable()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        when (it) {
                            is TrackEvent -> updateTrack(it.trackId)
                            is StateEvent -> updateState(it.state)
                            is GameEvent -> updateGame(it.gameId)
                        }
                    }
        }

        player?.playingGameId?.let {
            updateGame(it)
        }
    }

    fun unsubscribeFromUpdates() {
        subscription?.unsubscribe()
        subscription = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.getAction()
        logDebug("[MediaNotificationManager] Received intent: ${action}")
        when (action) {
            ACTION_PAUSE -> transportControls?.pause()
            ACTION_PLAY -> transportControls?.play()
            ACTION_PREV -> transportControls?.skipToPrevious()
            ACTION_NEXT -> transportControls?.skipToNext()
            ACTION_STOP -> transportControls?.stop()
            else -> logError("[MediaNotificationManager] Unknown intent ignored: ${action}")
        }
    }

    /**
     * Posts the notification and starts tracking the session to keep it
     * updated. The notification will automatically be removed if the session is
     * destroyed before [.stopNotification] is called.
     */
    fun startNotification() {
        if (!notified) {
            val player = playerService.player
            if (player != null) {
                val localTrackId = player.playingTrackId ?: player.pausedTrackId

                if (localTrackId != null) {
                    updateState(player.state)
                    updateTrack(localTrackId)

                    val notification = createNotification()
                    if (notification != null) {
                        logVerbose("[MediaNotificationManager] Starting foreground notification...")

                        val filter = IntentFilter()
                        filter.addAction(ACTION_PAUSE)
                        filter.addAction(ACTION_PLAY)
                        filter.addAction(ACTION_STOP)
                        filter.addAction(ACTION_PREV)
                        filter.addAction(ACTION_NEXT)

                        playerService.registerReceiver(this, filter)
                        playerService.startForeground(NOTIFICATION_ID, notification)

                        notified = true
                    }
                } else {
                    logError("[MediaNotificationManager] Can't show notification: no track found.")
                }
            }
        }
    }

    /**
     * Removes the notification and stops tracking the session. If the session
     * was destroyed this has no effect.
     */
    fun stopNotification() {
        if (notified) {
            logDebug("[MediaNotificationManager] Stopping notification.")

            notified = false
            mediaController?.unregisterCallback(controllerCallback)

            try {
                notificationService.cancel(NOTIFICATION_ID)
                playerService.unregisterReceiver(this)
            } catch (ex: IllegalArgumentException) {
                // ignore if the receiver is not registered.
            }

            playerService.stopForeground(true)
            playingGameArtBitmap = null
        }
    }

    /**
     * A workaround for the fact that controllerCallback is null inside the init {} constructor.
     */
    fun setControllerCallback() {
        mediaController?.registerCallback(controllerCallback)
    }

    /**
     *      Private Methods
     */

    private fun updateTrack(trackId: String?) {
        logDebug("[MediaNotificationManager] Updating notification track.")

        if (trackId != null) {
            val realm = Realm.getDefaultInstance()
            playingTrack = repository?.getTrackSync(trackId)

            mediaMetadata = updateMetadata()
            playerService.session?.setMetadata(mediaMetadata)
        } else {
            notificationService.cancelAll()
        }
    }

    private fun updateGame(gameId: String?) {
        logDebug("[MediaNotificationManager] Updating notification game.")

        val realm = Realm.getDefaultInstance()
        val game = if (gameId != null) repository?.getGameSync(gameId) else null

        val imagePath = game?.artLocal ?: Game.PICASSO_ASSET_ALBUM_ART_BLANK

        if (imagePath != playingGameArtPath) {
            playingGameArtPath = imagePath

            loadBitmapLowQuality(playerService, imagePath)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        playingGameArtBitmap = it

                        mediaMetadata = updateMetadata()
                        playerService.session?.setMetadata(mediaMetadata)
                    }
        }
        playingGame = game
        mediaMetadata = updateMetadata()
        playerService.session?.setMetadata(mediaMetadata)
    }


    private fun updateState(state: Int) {
        logDebug("[MediaNotificationManager] Updating notification state.")

        val player = playerService.player

        if (player != null) {
            var position = player.position

            val actions = getAvailableActions(player.state, player.playbackQueuePosition, player.playbackQueue?.size)

            val stateBuilder = PlaybackStateCompat.Builder().setActions(actions)
            stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime())

            playbackState = stateBuilder.build()
            playerService.session?.setPlaybackState(playbackState)
        }
    }

    private fun createNotification(): Notification? {
        if (mediaMetadata == null || playbackState == null) {
            logError("[MediaNotificationManager] Can't create notification. " +
                    "Playback state: ${playbackState} " +
                    "Metadata: ${mediaMetadata}")
            return null
        }

        logDebug("[MediaNotificationManager] Creating notification.")

        val session = playerService.session ?: return null

        val notificationBuilder = builderFrom(playerService, session) ?: return null

        var playButtonPosition = 0

        val actions = playbackState?.actions

        if (actions != null) {
            // If skip to previous action is enabled
            if ((actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L) {
                notificationBuilder.addAction(R.drawable.ic_skip_previous_black_24dp,
                        playerService.getString(R.string.notification_label_prev), prevIntent)

                /*
                * If there is a "skip to previous" button, the play/pause button will
                * be the second one. We need to keep track of it, because the MediaStyle notification
                * requires to specify the index of the buttons (actions) that should be visible
                * when in compact view.
                */
                playButtonPosition = 1
            }
        }

        addPlayPauseAction(notificationBuilder)

        if (actions != null) {
            // If skip to next action is enabled
            if ((actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L) {
                notificationBuilder.addAction(R.drawable.ic_skip_next_black_24dp,
                        playerService.getString(R.string.notification_label_next), nextIntent)
            }
        }

        val notificationIcon = if (playbackState?.state == PlaybackState.STATE_PLAYING)
            R.drawable.ic_stat_play
        else
            R.drawable.ic_stat_pause

        val stop = getActionIntent(playerService, KeyEvent.KEYCODE_MEDIA_STOP)

        val mediaStyle = NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(*intArrayOf(playButtonPosition))
                .setMediaSession(sessionToken)
                .setShowCancelButton(true)
                .setCancelButtonIntent(stop)

        notificationBuilder.setStyle(mediaStyle)
                .setDeleteIntent(stop)
                .setSmallIcon(notificationIcon)
                .setColor(ContextCompat.getColor(playerService, R.color.primary_dark))

        setNotificationPlaybackState(notificationBuilder)

        return notificationBuilder.build()
    }

    private fun addPlayPauseAction(builder: NotificationCompat.Builder) {
        logDebug("[MediaNotificationManager] Updating play/pause button.")

        val label: String
        val icon: Int
        val intent: PendingIntent

        if (playbackState?.state == PlaybackState.STATE_PLAYING) {
            label = playerService.getString(R.string.notification_label_pause)
            icon = R.drawable.ic_pause_black_24dp
            intent = pauseIntent
        } else {
            label = playerService.getString(R.string.notification_label_play)
            icon = R.drawable.ic_play_arrow_black_24dp
            intent = playIntent
        }
        builder.addAction(Action(icon, label, intent))
    }

    private fun setNotificationPlaybackState(builder: NotificationCompat.Builder) {
        logDebug("[MediaNotificationManager] Updating notification's playback state.")

        if (playbackState == null || !notified) {
            logVerbose("[MediaNotificationManager] Canceling notification.")

            playerService.stopForeground(true)
            return
        }

        // Make sure that the notification can be dismissed by the user when we are not playing:
        builder.setOngoing(playbackState?.getState() == PlaybackState.STATE_PLAYING)
    }

    private fun updateMetadata(): MediaMetadataCompat? {
        playingTrack?.let {
            val metadataBuilder = Track.toMetadataBuilder(it)

            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, playingGame?.title ?: "Unknown")

            if (playingGameArtBitmap != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, playingGameArtBitmap)
            } else {
                logError("[MediaNotificationManager] Couldn't load game art.")
            }

            return metadataBuilder.build()
        }

        return null
    }

    private fun getAvailableActions(state: Int, queuePosition: Int?, queueSize: Int?): Long {
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_STOP

        if (state == PlaybackState.STATE_PLAYING) {
            actions = actions or PlaybackState.ACTION_PAUSE
        }

        if (queuePosition != null && queueSize != null) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS

            if (queuePosition < queueSize - 1) {
                actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
            }
        }

        return actions
    }

    /**
     * Update the state based on a change on the session token. Called either when
     * we are running for the first time or when the media session owner has destroyed the session
     * (see [android.media.session.MediaController.Callback.onSessionDestroyed])
     */
    private fun updateSessionToken() {
        val freshToken = playerService.getSessionToken()

        if (sessionToken == null || sessionToken != freshToken) {
            sessionToken = freshToken

            mediaController?.unregisterCallback(controllerCallback)

            mediaController = MediaControllerCompat(playerService, sessionToken)

            transportControls = mediaController?.transportControls

            if (controllerCallback != null) {
                mediaController?.registerCallback(controllerCallback)
            }
        }
    }

    /**
     *      Listeners & Callbacks
     */

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            logDebug("[MediaNotificationManager] Playback state changed: ${state}")
            playbackState = state

            if (state.state == PlaybackState.STATE_STOPPED || state.state == PlaybackState.STATE_NONE) {
                stopNotification()
            } else {
                if (state.state == PlaybackState.STATE_PAUSED) {
                    playerService.stopForeground(false)
                }

                val notification = createNotification()
                if (notification != null) {
                    notificationService.notify(NOTIFICATION_ID, notification)
                }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            logDebug("[MediaNotificationManager] Metadata changed: ${metadata}")
            mediaMetadata = metadata

            val notification = createNotification()
            if (notification != null) {
                notificationService.notify(NOTIFICATION_ID, notification)
            }
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            logDebug("[MediaNotificationManager] Session destroyed; resetting session token.")
            updateSessionToken()
        }
    }



    companion object {
        val REQUEST_CODE = 1234

        val NOTIFICATION_ID = 5678

        val ACTION_PLAY = "${BuildConfig.APPLICATION_ID}.play"
        val ACTION_PAUSE = "${BuildConfig.APPLICATION_ID}.pause"
        val ACTION_PREV = "${BuildConfig.APPLICATION_ID}.prev"
        val ACTION_NEXT = "${BuildConfig.APPLICATION_ID}.next"
        val ACTION_STOP = "${BuildConfig.APPLICATION_ID}.stop"

        /**
         * Build a notification using the information from the given media session. Makes heavy use
         * of [MediaMetadataCompat.getDescription] to extract the appropriate information.
         * @param context Context used to construct the notification.
         * *
         * @param mediaSession Media session to get information.
         * *
         * @return A pre-built notification with information from the given media session.
         */
        fun builderFrom(context: Context, mediaSession: MediaSessionCompat): NotificationCompat.Builder? {
            val controller = mediaSession.controller
            val mediaMetadata = controller.metadata
            val description = mediaMetadata?.description ?: return null

            val builder = NotificationCompat.Builder(context)

            builder.setContentTitle(description.title)
                    .setContentText(description.subtitle)
                    .setSubText(description.description)
                    .setLargeIcon(description.iconBitmap)
                    .setContentIntent(controller.sessionActivity)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            return builder
        }

        /**
         * Create a [PendingIntent] appropriate for a MediaStyle notification's action. Assumes
         * you are using a media button receiver.
         * @param context Context used to contruct the pending intent.
         * *
         * @param mediaKeyEvent KeyEvent code to send to your media button receiver.
         * *
         * @return An appropriate pending intent for sending a media button to your media button
         * *      receiver.
         */
        fun getActionIntent(context: Context, mediaKeyEvent: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)

            intent.setPackage(context.packageName)
            intent.putExtra(Intent.EXTRA_KEY_EVENT,
                    KeyEvent(KeyEvent.ACTION_DOWN, mediaKeyEvent))

            return PendingIntent.getBroadcast(context, mediaKeyEvent, intent, 0)
        }
    }
}