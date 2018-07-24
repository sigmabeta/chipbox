package net.sigmabeta.chipbox.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import com.crashlytics.android.Crashlytics
import io.reactivex.disposables.CompositeDisposable
import net.sigmabeta.chipbox.className
import net.sigmabeta.chipbox.model.repository.Repository
import timber.log.Timber
import java.io.IOException
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

    internal var view: V? = null

    protected var setupStartTime = 0L

    protected var subscriptions = CompositeDisposable()

    protected var state = UiState.NONE
        set(value) {
            Timber.v("%s Setting new state: %s", className(), value)
            when (value) {
                UiState.ERROR -> view?.showErrorState()
                UiState.EMPTY -> view?.showEmptyState()
                UiState.LOADING -> view?.showLoadingState()
                UiState.READY -> showReadyState()
                else -> { /* no-op */ }
            }
            field = value
        }

    fun onResume(view: V) {
        this.view = view
        Timber.v("%s resuming with state: %s", className(), state)

        when (state) {
            UiState.NONE -> handleError(IllegalStateException("NONE state detected in class ${this::class.java.simpleName}"), null)
            UiState.CANCELED -> view.requestReSetup()
            UiState.ERROR -> view.showErrorState()
            UiState.EMPTY -> view.showEmptyState()
            UiState.LOADING -> view.showLoadingState()
            UiState.READY -> showReadyState()
        }
    }

    fun onPause() {
        subscriptions.clear()

        if (state == UiState.LOADING) {
            state = UiState.CANCELED
        }
    }

    fun onImageLoadSuccess() {
        printBenchmark("Image loaded")
    }

    fun onImageLoadError() {
        handleError(IOException("Image load failed."))
    }

    /**
     * Error Handling
     */

    protected fun handleError(error: Throwable, action: View.OnClickListener? = null) {
        Timber.e(Log.getStackTraceString(error))
        state = UiState.ERROR

        when (error) {
            is InvalidClearViewException -> view?.showError("A previous instance of this screen tried to clear the Presenter's reference to it. "
                    + "Please check that all animations (including loading spinners) were cleared.")
            else -> view?.showError(error.message ?: "Unknown error.")
        }
        Crashlytics.logException(error)
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
    abstract fun showReadyState()

    /**
     * Handle click events.
     */
    abstract fun onClick(id: Int)

    protected fun clearView() {
        this.view = null
    }
}
