package net.sigmabeta.chipbox.ui

import android.os.Bundle
import net.sigmabeta.chipbox.ui.BaseView
import rx.subscriptions.CompositeSubscription

abstract class BasePresenter {
    var subscriptions = CompositeSubscription()

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

    abstract fun setView(view: BaseView)

    abstract fun clearView()
}
