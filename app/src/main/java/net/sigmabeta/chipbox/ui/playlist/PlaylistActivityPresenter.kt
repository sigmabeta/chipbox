package net.sigmabeta.chipbox.ui.playlist

import android.os.Bundle
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseView
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistActivityPresenter @Inject constructor() : ActivityPresenter() {
    var view: PlaylistActivityView? = null

    /**
     * ActivityPresenter
     */

    override fun onReenter() = Unit

    override fun onReCreate(savedInstanceState: Bundle) = Unit

    override fun onTempDestroy() = Unit

    /**
     * BasePresenter
     */

    override fun setup(arguments: Bundle?) {
        view?.showPlaylistFragment()
    }

    override fun teardown() = Unit

    override fun updateViewState() = Unit

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is PlaylistActivityView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    /**
     * Private Methods
     */
}