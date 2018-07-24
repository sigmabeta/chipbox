package net.sigmabeta.chipbox.backend

import android.support.v4.media.session.MediaSessionCompat
import timber.log.Timber

class SessionCallback(val playerService: PlayerService) : MediaSessionCompat.Callback() {
    override fun onPlay() {
        Timber.v("Received PLAY command.")

        playerService.player?.start(null)
    }

    override fun onStop() {
        Timber.v("Received STOP command.")

        playerService.player?.stop()
    }

    override fun onPause() {
        Timber.v("Received PAUSE command.")

        playerService.player?.pause()
    }

    override fun onSkipToNext() {
        Timber.v("Received NEXT command.")

        playerService.player?.skipToNext()
    }

    override fun onSkipToPrevious() {
        Timber.v("Received PREV command.")

        playerService.player?.skipToPrev()
    }
}
