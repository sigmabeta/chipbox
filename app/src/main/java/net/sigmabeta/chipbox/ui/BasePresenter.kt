package net.sigmabeta.chipbox.ui

import android.os.Bundle
import net.sigmabeta.chipbox.util.logError
import rx.subscriptions.CompositeSubscription

abstract class BasePresenter {
    var sharedElementPreDrawn = false
    var sharedImageLoaded = false

    var subscriptions = CompositeSubscription()

    fun onResume() {
        updateViewState()
    }

    fun onPause() {
        subscriptions.unsubscribe()
        subscriptions = CompositeSubscription()
    }

    fun onSharedPreDraw() {
        sharedElementPreDrawn = true
        attemptTransitionStart()
    }

    fun onSharedImageLoaded() {
        sharedImageLoaded = true
        attemptTransitionStart()
    }

    fun attemptTransitionStart() {
        if (sharedElementPreDrawn && sharedImageLoaded) {
            getView()?.startTransition()
        } else {
            logError("[FragmentPresenter] Skipping transition: sharedImageLoaded = $sharedImageLoaded sharedElementPreDrawn = $sharedElementPreDrawn")
        }
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

    abstract fun getView(): BaseView?

    abstract fun setView(view: BaseView)

    abstract fun clearView()
}
