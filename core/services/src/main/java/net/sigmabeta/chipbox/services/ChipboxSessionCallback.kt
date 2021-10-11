package net.sigmabeta.chipbox.services

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.services.NotificationGenerator.Companion.NOTIFICATION_ID
import net.sigmabeta.chipbox.services.transformers.toMetadataBuilder
import net.sigmabeta.chipbox.services.transformers.toPlaybackStateBuilder
import timber.log.Timber

class ChipboxSessionCallback(
    private val service: ChipboxPlaybackService,
    private val serviceScope: CoroutineScope,
    private val director: Director,
    private val notificationGenerator: NotificationGenerator
) : MediaSessionCompat.Callback() {
    init {
        collectDirectorState()
    }

    lateinit var mediaSession: MediaSessionCompat

    private val intentFilter = IntentFilter(ACTION_AUDIO_BECOMING_NOISY)

    private lateinit var audioFocusRequest: AudioFocusRequestCompat

    private val noisyReceiver = BecomingNoisyReceiver()

    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
        super.onPlayFromMediaId(mediaId, extras)
        Timber.d("Received command to play $mediaId.")

        if (mediaId != null) {
            IdToCommandParser.parse(director, mediaId)
            playHelper()
        }
    }

    override fun onPlay() {
        Timber.d("Received 'play' command.")
        playHelper()
    }

    override fun onPause() {
        Timber.d("Received 'pause' command.")

        // Update metadata and state
        // TODO

        // pause the director (custom call)
        director.pause()

        // unregister BECOME_NOISY BroadcastReceiver
        service.unregisterReceiver(noisyReceiver)

        // Take the service out of the foreground, retain the notification
        service.stopForeground(false)
    }

    override fun onStop() {
        Timber.d("Received 'stop' command.")
        val audioManagerService = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Abandon audio focus
        AudioManagerCompat.abandonAudioFocusRequest(audioManagerService, audioFocusRequest)
        service.unregisterReceiver(noisyReceiver)

        // Stop the service
        service.stopSelf()

        // Set the session inactive  (and update metadata and state)
        mediaSession.isActive = false

        // stop the director (custom call)
        director.stop()

        // Take the service out of the foreground
        service.stopForeground(false)
    }

    private fun playHelper() {
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

        val result = AudioManagerCompat.requestAudioFocus(audioManagerService, audioFocusRequest)

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // Start the service
            val intent = Intent(service, ChipboxPlaybackService::class.java)
            service.startService(intent)

            // Set the session active  (and update metadata and state)
            mediaSession.isActive = true

            // start the director (custom call)
            director.play()

            // Register BECOME_NOISY BroadcastReceiver
            service.registerReceiver(noisyReceiver, intentFilter)

            // Put the service in the foreground, post notification
            val notification = notificationGenerator.generate(mediaSession)
            service.startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun collectDirectorState() {
        serviceScope.launch {
            director.metadataState()
                .collect { track ->
                    mediaSession.setMetadata(
                        track.toMetadataBuilder().build()
                    )
                }
        }

        serviceScope.launch {
            director.playbackState()
                .collect { track ->
                    mediaSession.setPlaybackState(
                        track.toPlaybackStateBuilder().build()
                    )
                }
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
