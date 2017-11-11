package net.sigmabeta.chipbox.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import com.crashlytics.android.Crashlytics
import net.sigmabeta.chipbox.model.repository.Repository
import rx.subscriptions.CompositeSubscription
import timber.log.Timber
import javax.inject.Inject

abstract class BasePresenter<V : BaseView> {
    /**
     * Injected Variables
     */

    lateinit var repository: Repository
        @Inject set

    /**
     * Protected Variables
     */

    protected var view: V? = null

    protected var needsSetup: Boolean = false

    protected var loading = false
        set (value) {
            field = value
            if (view != null) {
                if (field) {
                    view?.showLoading()
                } else {
                    view?.hideLoading()
                }
            }
        }

    protected var setupStartTime = 0L

    protected var subscriptions = CompositeSubscription()

    fun onResume(view: V) {
        this.view = view

        if (!needsSetup) {
            updateViewState()
        } else if (!loading) {
            view.requestReSetup()
        }

        needsSetup = false
    }

    fun onPause() {
        subscriptions.clear()
    }

    /**
     * Error Handling
     */

    protected fun handleError(error: Throwable, action: View.OnClickListener?) {
        Timber.e(Log.getStackTraceString(error))
        loading = false

        when (error) {
            is InvalidClearViewException -> handleInvalidClearError(error)
        }
        Crashlytics.logException(error)
    }

    protected fun handleInvalidClearError(error: InvalidClearViewException) {
        view?.showInvalidClearError(error)
    }

    /**
     * Other Protected Methods
     */

    protected fun printBenchmark(eventName: String) {
        if (setupStartTime > 0) {
            val timeDiff = System.currentTimeMillis() - setupStartTime
            Timber.i("Benchmark: %s after %d ms.", eventName, timeDiff)
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

    /**
     * Handle click events.
     */
    abstract fun onClick(id: Int)

    protected fun clearView() {
        this.view = null
    }
}
