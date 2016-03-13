package net.sigmabeta.chipbox.presenter

import android.os.Bundle
import net.sigmabeta.chipbox.view.interfaces.BaseView
import net.sigmabeta.chipbox.view.interfaces.PlayerActivityView
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerActivityPresenter @Inject constructor() : ActivityPresenter() {
    var view: PlayerActivityView? = null


    override fun onReCreate(savedInstanceState: Bundle) {
    }

    override fun onTempDestroy() {
    }

    override fun setup(arguments: Bundle?) {
        view?.showPlayerFragment()
    }

    override fun teardown() {
    }

    override fun updateViewState() {
    }

    override fun setView(view: BaseView) {
        if (view is PlayerActivityView) this.view = view
    }

    override fun clearView() {
        view = null
    }
}