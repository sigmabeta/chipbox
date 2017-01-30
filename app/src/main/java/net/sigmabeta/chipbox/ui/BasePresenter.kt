package net.sigmabeta.chipbox.ui

import android.os.Bundle
import net.sigmabeta.chipbox.model.repository.Repository
import rx.subscriptions.CompositeSubscription
import javax.inject.Inject

abstract class BasePresenter {
    var subscriptions = CompositeSubscription()

    lateinit var repository: Repository
        @Inject set

    fun onResume() {
        updateViewState()
    }

    fun onPause() {
        subscriptions.unsubscribe()
        subscriptions = CompositeSubscription()
    }

    /**
     * Begin any expensive network or database operations.
     */
    abstract fun setup(arguments: Bundle?)

    /**
     * Remove any state from the current View.
     */
    abstract fun teardown()

    /**
     * Set all views to reflect the current state.
     */
    abstract fun updateViewState()

    /**
     * Handle click events.
     */
    abstract fun onClick(id: Int)

    abstract fun getView(): BaseView?

    abstract fun setView(view: BaseView)

    abstract fun clearView()
}
