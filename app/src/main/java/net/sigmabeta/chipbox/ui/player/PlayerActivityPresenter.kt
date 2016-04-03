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

    fun onClick(id: Int) {
        when (id) {
            R.id.button_fab -> {
                playlistVisible = true
                view?.showPlaylistFragment()
            }

            R.string.back_button -> {
                if (playlistVisible) {
                    view?.hidePlaylistFragment()
                    playlistVisible = false
                } else {
                    view?.callFinish()
                }
            }
        }
    }

    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun onTempDestroy() {
    }

    override fun setup(arguments: Bundle?) {
        view?.showControlsFragment()
        view?.showPlayerFragment()
    }

    override fun teardown() {
        playlistVisible = false
    }

    override fun updateViewState() {
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