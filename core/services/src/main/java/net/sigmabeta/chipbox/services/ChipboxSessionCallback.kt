package net.sigmabeta.chipbox.services

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.services.NotificationGenerator.Companion.NOTIFICATION_ID
import net.sigmabeta.chipbox.services.transformers.toAndroidXPlaybackState
import net.sigmabeta.chipbox.services.transformers.toMetadataBuilder
import timber.log.Timber

class ChipboxSessionCallback(
    private val service: ChipboxPlaybackService,
    private val serviceScope: CoroutineScope,
    private val director: Director,
    private val notificationGenerator: NotificationGenerator,
    private val systemNotifService: NotificationManagerCompat
) : MediaSessionCompat.Callback() {
    init {
        collectDirectorState()
    }

    lateinit var mediaSession: MediaSessionCompat

    private val intentFilter = IntentFilter(ACTION_AUDIO_BECOMING_NOISY)

    private var audioFocusRequest: AudioFocusRequestCompat? = null

    private val noisyReceiver = BecomingNoisyReceiver()

    private var noisyReceiverActive = false

    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
        super.onPlayFromMediaId(mediaId, extras)
        Timber.d("Received command to play $mediaId.")

        if (mediaId != null) {
            IdToCommandParser.handleCommand(director, mediaId)
            playHelper()
        }
    }

    override fun onPlay() {
        Timber.d("Received 'play' command.")
        playHelper()
    }

    override fun onPause() {
        Timber.d("Received 'pause' command.")
        director.pause()
    }

    override fun onStop() {
        Timber.d("Received 'stop' command.")
        director.stop()
    }

    private fun playHelper() {
        val result = requestAudioFocus()
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Timber.v("Focus request granted, starting service...")

            // start the director (custom call)
            director.play()
        } else {
            Timber.e("Failed to get Audiofocus: $result")
        }
    }

    private fun requestAudioFocus(): Int {
        val audioManagerService = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val attributes = AudioAttributesCompat
            .Builder()
            .apply {
                setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                setUsage(AudioAttributesCompat.USAGE_MEDIA)
            }
            .build()

        audioFocusRequest = AudioFocusRequestCompat
            .Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .apply {
                setOnAudioFocusChangeListener(focusListener)
                setAudioAttributes(attributes)
            }
            .build()

        val result = AudioManagerCompat.requestAudioFocus(audioManagerService, audioFocusRequest!!)
        return result
    }

    private fun collectDirectorState() {
        serviceScope.launch {
            director.metadataState()
                .collect { track ->
                    mediaSession.setMetadata(
                        track
                            .toMetadataBuilder()
                            .build()
                    )
                    val notif = notificationGenerator.generate(mediaSession)
                    systemNotifService.notify(NOTIFICATION_ID, notif)
                }
        }

        serviceScope.launch {
            director.playbackState()
                .collect { chipboxPlaybackState ->
                    mediaSession.setPlaybackState(
                        chipboxPlaybackState.toAndroidXPlaybackState()
                    )

                    if (chipboxPlaybackState.state != PlayerState.STOPPED) {
                        val notif = notificationGenerator.generate(mediaSession)
                        systemNotifService.notify(NOTIFICATION_ID, notif)

                        handlePlaybackState(chipboxPlaybackState)
                    }
                }
        }
    }

    private fun handlePlaybackState(cps: ChipboxPlaybackState) {
        when (cps.state) {
            PlayerState.STOPPED -> handleStoppedState()
            PlayerState.PAUSED -> handlePausedState()
            PlayerState.PLAYING -> handlePlayingState()
            else -> Timber.v("Unhandled playback state: ${cps.state}")
        }
    }

    private fun handlePlayingState() {
        // Start the service
        val intent = Intent(service, ChipboxPlaybackService::class.java)
        service.startService(intent)

        // Set the session active  (and update metadata and state)
        mediaSession.isActive = true

        setNoisyReceiverActive(true)

        // Put the service in the foreground, post notification
        val notification = notificationGenerator.generate(mediaSession)
        service.startForeground(
            NOTIFICATION_ID,
            notification
        )
    }

    private fun handlePausedState() {
        setNoisyReceiverActive(false)

        // Take the service out of the foreground, retain the notification
        service.stopForeground(false)
    }

    private fun handleStoppedState() {
        val audioManagerService = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Abandon audio focus
        audioFocusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManagerService, it)
        }

        setNoisyReceiverActive(false)

        // Stop the service
        service.stopSelf()

        // Set the session inactive  (and update metadata and state)
        mediaSession.isActive = false

        // Take the service out of the foreground
        service.stopForeground(false)
    }

    private fun setNoisyReceiverActive(active: Boolean) {
        if (active && !noisyReceiverActive) {
            service.registerReceiver(noisyReceiver, intentFilter)
            noisyReceiverActive = true
            return
        }

        if (!active && noisyReceiverActive) {
            service.unregisterReceiver(noisyReceiver)
            noisyReceiverActive = false
        }
    }

    private var focusListener = AudioManager.OnAudioFocusChangeListener { focusChangeType ->
        when (focusChangeType) {
            AudioManager.AUDIOFOCUS_LOSS -> director.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> director.pauseTemporarily()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> director.duck()
            AudioManager.AUDIOFOCUS_GAIN -> director.resumeFocus()
        }
    }

}
