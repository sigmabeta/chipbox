package net.sigmabeta.chipbox.view.activity

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.dagger.injector.ActivityInjector
import net.sigmabeta.chipbox.presenter.PlayerActivityPresenter
import net.sigmabeta.chipbox.view.fragment.PlayerFragment
import net.sigmabeta.chipbox.view.interfaces.FragmentContainer
import net.sigmabeta.chipbox.view.interfaces.PlayerActivityView
import javax.inject.Inject

class PlayerActivity : BaseActivity(), PlayerActivityView, FragmentContainer {
    var presenter: PlayerActivityPresenter? = null
        @Inject set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_player)

        val launcher = intent

        val fragmentArg = launcher.getLongExtra(ARGUMENT_FRAGMENT_ARG, -1)

        presenter?.onCreate(fragmentArg)
    }

    /**
     * PlayerView
     */

    override fun showPlayerFragment(trackId: Long) {
        var fragment = PlayerFragment.newInstance(trackId)

        supportFragmentManager.beginTransaction()
                .add(R.id.frame_fragment, fragment, PlayerFragment.FRAGMENT_TAG)
                .commit()
    }

    /**
     * FragmentContainer
     */

    override fun setActivityTitle(title: String) {
        setTitle(title)
    }

    /**
     * Private Methods
     */

    private fun connectToSession(token: MediaSession.Token) {
        val mediaController = MediaController(this, token)

        setMediaController(mediaController)
        mediaController.registerCallback(controllerCallback)
    }

    /**
     * BaseActivity
     */

    override fun inject() {
        ActivityInjector.inject(this)
    }

    /**
     * Callbacks and Listeners
     */

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
        }
    }

    companion object {
        val ACTIVITY_TAG = "${BuildConfig.APPLICATION_ID}.player"

        val ARGUMENT_FRAGMENT_ARG = "${ACTIVITY_TAG}.fragment_argument"

        fun launch(context: Context, fragmentArg: Long) {
            val launcher = Intent(context, PlayerActivity::class.java)

            launcher.putExtra(ARGUMENT_FRAGMENT_ARG, fragmentArg)

            context.startActivity(launcher)
        }
    }
}