package net.sigmabeta.chipbox.services

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.services.NotificationGenerator.Companion.NOTIFICATION_ID
import javax.inject.Inject

class ChipboxServiceCallback @Inject constructor(
    private val director: Director,
    private val notificationGenerator: NotificationGenerator
) : MediaSessionCompat.Callback() {
    lateinit var service: ChipboxPlaybackService

    private val intentFilter = IntentFilter(ACTION_AUDIO_BECOMING_NOISY)

    private lateinit var mediaSession: MediaSessionCompat

    private lateinit var focusListener: AudioManager.OnAudioFocusChangeListener

    private lateinit var audioFocusRequest: AudioFocusRequestCompat

    private val myNoisyAudioStreamReceiver = BecomingNoisyReceiver()

    override fun onPlay() {
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
            service.registerReceiver(myNoisyAudioStreamReceiver, intentFilter)

            // Put the service in the foreground, post notification
            val notification = notificationGenerator.generate(mediaSession)
            service.startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    override fun onStop() {
        val audioManagerService = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Abandon audio focus
        AudioManagerCompat.abandonAudioFocusRequest(audioManagerService, audioFocusRequest)
        service.unregisterReceiver(myNoisyAudioStreamReceiver)

        // Stop the service
        service.stopSelf()

        // Set the session inactive  (and update metadata and state)
        mediaSession.isActive = false

        // stop the director (custom call)
        director.stop()

        // Take the service out of the foreground
        service.stopForeground(false)
    }

    override fun onPause() {
        // Update metadata and state
        // pause the director (custom call)
        director.pause()

        // unregister BECOME_NOISY BroadcastReceiver
        service.unregisterReceiver(myNoisyAudioStreamReceiver)

        // Take the service out of the foreground, retain the notification
        service.stopForeground(false)
    }
}
