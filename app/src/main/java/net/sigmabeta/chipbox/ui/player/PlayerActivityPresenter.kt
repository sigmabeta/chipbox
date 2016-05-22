package net.sigmabeta.chipbox.ui.player

import android.os.Bundle
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseView
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerActivityPresenter @Inject constructor() : ActivityPresenter() {
    var view: PlayerActivityView? = null

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
        view?.showControlsFragment()
        view?.showPlayerFragment()
    }

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onTempDestroy() = Unit

    override fun teardown() {
        playlistVisible = false
    }

    override fun updateViewState() {
        if (playlistVisible) {
            view?.showStatusBar()
        } else {
            view?.hideStatusBar()
        }
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is PlayerActivityView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    override fun onReenter() {
    }
}