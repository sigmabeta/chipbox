package net.sigmabeta.chipbox.ui

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.squareup.picasso.Callback
import net.sigmabeta.chipbox.dagger.component.FragmentComponent
import net.sigmabeta.chipbox.util.logError

abstract class BaseActivity : AppCompatActivity(), BaseView {
    val sharedPreDrawListener = object : ViewTreeObserver.OnPreDrawListener {
        var sharedView: View? = null

        override fun onPreDraw(): Boolean {
            sharedView?.viewTreeObserver?.removeOnPreDrawListener(this)
            getPresenter().onSharedPreDraw()
            return true
        }
    }

    fun getPicassoCallback(): Callback {
        return object : Callback {
            override fun onSuccess() {
                getSharedImage()?.viewTreeObserver?.addOnPreDrawListener(sharedPreDrawListener)
            }

            override fun onError() {
                getSharedImage()?.viewTreeObserver?.addOnPreDrawListener(sharedPreDrawListener)
                logError("[PlayerFragment] Couldn't load image.")
            }
        }
    }

    /**
     * Calls the superclass constructor, and then automatically
     * requests an injection of the Activity's dependencies.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inject()
        setContentView(getLayoutId())

        configureViews()
        getPresenter().onCreate(intent.extras, savedInstanceState, this)

        val sharedView = getSharedImage()

        if (sharedView != null) {
            setResult(RESULT_OK)
            if (sharedView is ImageView) {
                postponeEnterTransition()
            }
        } else if (shouldDelayTransitionForFragment()) {
            setResult(RESULT_OK)
            postponeEnterTransition()
        }
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        super.onActivityReenter(resultCode, data)
        getPresenter().onReenter()
    }

    override fun onResume() {
        super.onResume()
        getPresenter().onResume()
    }

    override fun onPause() {
        super.onPause()
        getPresenter().onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        getPresenter().onDestroy(isFinishing)
    }

    override fun startTransition() {
        startPostponedEnterTransition()
    }

    override fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showErrorSnackbar(message: String, action: View.OnClickListener?, actionLabel: Int?) {
        val snackbar = Snackbar.make(getContentLayout(), message, Snackbar.LENGTH_LONG)

        if (action != null && actionLabel != null) {
            snackbar.setAction(actionLabel, action)
        }

        snackbar.show()
    }

    fun getFragmentComponent(): FragmentComponent {
        return getPresenter().fragmentComponent
    }

    /**
     * Must be overridden to request the activity's dependencies
     * and do any other necessary setup.
     */
    protected abstract fun inject()

    protected abstract fun getPresenter(): ActivityPresenter

    /**
     * Perform any necessary run-time setup of views: RecyclerViews, ClickListeners,
     * etc.
     */
    protected abstract fun configureViews()

    protected abstract fun getLayoutId(): Int

    protected abstract fun getContentLayout(): FrameLayout

    protected abstract fun getSharedImage(): View?

    /**
     * Return true here if the Activity contains a Fragment with a SharedElement view inside
     * it for whose readiness we must wait before beginning the transition. If this Activity's
     * getSharedImage() call returns non-null, this is ignored.
     */
    protected abstract fun shouldDelayTransitionForFragment(): Boolean
}