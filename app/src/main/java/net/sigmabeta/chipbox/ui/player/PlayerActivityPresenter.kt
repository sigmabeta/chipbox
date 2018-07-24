package net.sigmabeta.chipbox.ui.player

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.UiState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerActivityPresenter @Inject constructor() : ActivityPresenter<PlayerActivityView>() {
    var playlistVisible = false

    override fun onClick(id: Int) {
        when (id) {
            R.id.button_fab -> {
                playlistVisible = true
                view?.showPlaylistFragment()
                view?.showStatusBar()
            }

            R.string.back_button -> {
                if (playlistVisible) {
                    view?.hidePlaylistFragment()
                    view?.hideStatusBar()

                    playlistVisible = false
                } else {
                    view?.callFinish()
                }
            }
        }
    }

    override fun setup(arguments: Bundle?) {
        state = UiState.READY

        view?.showControlsFragment()
        view?.showPlayerFragment()
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onTempDestroy() = Unit

    override fun teardown() {
        playlistVisible = false
    }

    override fun showReadyState() {
        if (playlistVisible) {
            view?.showStatusBar()
        } else {
            view?.hideStatusBar()
        }
    }
    override fun onReenter() {
    }
}