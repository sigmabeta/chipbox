package net.sigmabeta.chipbox.ui.playlist

import android.os.Bundle
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.dagger.scope.ActivityScoped
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentPresenter
import net.sigmabeta.chipbox.util.logWarning
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

@ActivityScoped
class PlaylistFragmentPresenter @Inject constructor(val player: Player) : FragmentPresenter() {
    var view: PlaylistFragmentView? = null

    /**
     * Public Methods
     */

    fun onItemClick(position: Long) {
        player.play(position.toInt())
    }

    /**
     * FragmentPresenter
     */

    override fun onReCreate(savedInstanceState: Bundle) = Unit

    /**
     * BasePresenter
     */

    override fun setup(arguments: Bundle?) {
    }

    override fun teardown() {
    }

    override fun updateViewState() {
        player.playbackQueue?.let {
            view?.showQueue(it)
        }

        val subscription = player.updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        else -> logWarning("[PlaylistFragmentPresenter] Unhandled ${it}")
                    }
                }

        subscriptions.add(subscription)
    }

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is PlaylistFragmentView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    /**
     * Private Methods
     */


}