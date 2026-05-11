package net.sigmabeta.chipbox.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

class AudioFocusHelper(
    context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onFocusLoss()
        fun onFocusLossTransient()
        fun onFocusLossTransientCanDuck()
        fun onFocusGain()
    }

    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var request: AudioFocusRequest? = null

    fun requestFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val newRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()

        return if (audioManager.requestAudioFocus(newRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            request = newRequest
            true
        } else {
            false
        }
    }

    fun abandonFocus() {
        request?.let { audioManager.abandonAudioFocusRequest(it) }
        request = null
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> callbacks.onFocusLoss()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> callbacks.onFocusLossTransient()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> callbacks.onFocusLossTransientCanDuck()
            AudioManager.AUDIOFOCUS_GAIN -> callbacks.onFocusGain()
        }
    }
}
