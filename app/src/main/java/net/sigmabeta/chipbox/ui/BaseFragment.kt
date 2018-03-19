package net.sigmabeta.chipbox.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.squareup.picasso.Callback
import net.sigmabeta.chipbox.ChipboxApplication
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.className
import timber.log.Timber

abstract class BaseFragment<out P : FragmentPresenter<in V>, in V : BaseView> : Fragment(), BaseView, View.OnClickListener {
    var injected = false
    var created = false

    override fun showErrorState() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun showEmptyState() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun getPicassoCallback(): Callback {
        return object : Callback {
            override fun onSuccess() {
                startTransition()
            }

            override fun onError() {
                startTransition()
                Timber.e("Couldn't load image.")
            }
        }
    }

    fun startTransition() {
        activity?.startPostponedEnterTransition()
    }

    fun setActivityTitle(title: String) {
        (activity as FragmentContainer).setTitle(title)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retainInstance = true
        created = true

        if (injected) {
            Timber.i("setting up presenter...")
            createHelper(savedInstanceState)
        } else {
            Timber.e("${className()} creating fragment, but not attached yet!")
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(getLayoutId(), container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureViews()
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        if (context is Activity) {
            attachHelper(context)
        }
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        attachHelper(activity)
    }

    override fun onResume() {
        super.onResume()
        getPresenterImpl().onResume(this as V)
    }

    override fun onPause() {
        super.onPause()
        getPresenterImpl().onPause()
    }

    override fun onDestroy() {
        super.onDestroy()

        val ending = activity.isFinishing || isRemoving
        getPresenterImpl().onDestroy(ending, this as V)

        created = false

        if (ending) {
            injected = false
        }
    }

    // TODO Enable this
    /*override fun onLowMemory() {
        super.onLowMemory()

        if (getTypedApplication().shouldShowDetailedErrors()) {
            showSnackbar("Memory low.", null, 0)
        }
    }*/

    override fun showToastMessage(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    override fun showErrorSnackbar(message: String, action: View.OnClickListener?, actionLabel: Int?) {
        val snackbar = Snackbar.make(getContentLayout(), message, Snackbar.LENGTH_LONG)

        if (action != null && actionLabel != null) {
            snackbar.setAction(actionLabel, action)
        }

        snackbar.show()
    }

    override fun getTypedApplication() = activity.application as ChipboxApplication

    fun showSnackbar(message: String, action: View.OnClickListener?, actionLabel: Int) {
        val snackbar = Snackbar.make(getContentLayout(), message, Snackbar.LENGTH_LONG)

        if (action != null && actionLabel > 0) {
            snackbar.setAction(actionLabel, action)
        }

        snackbar.show()
    }

    fun showSnackbarPermanent(message: String, action: View.OnClickListener?, actionLabel: Int) {
        val snackbar = Snackbar.make(getContentLayout(), message, Snackbar.LENGTH_INDEFINITE)

        if (action != null && actionLabel > 0) {
            snackbar.setAction(actionLabel, action)
        } else {
            snackbar.setAction(R.string.error_cta_dismiss) { snackbar.dismiss() }
        }

        snackbar.show()
    }

    fun getBaseActivity() = activity as BaseActivity<*, *>

    override fun requestReSetup() {
        getPresenterImpl().onCreate(arguments, null, this as V)
    }


    override fun onClick(clicked: View) {
        getPresenterImpl().onClick(clicked.id)
    }

    override fun showInvalidClearError(error: InvalidClearViewException) {
        showSnackbar("A previous instance of this screen tried to clear the Presenter's reference to it. "
                + "Please check that all animations (including loading spinners) were cleared.", null, 0)
    }

    var delayedViewOperation: Runnable? = null

    /**
     * Performs an operation (probably a view operation of some sort) only if this
     * Fragment is visible to the user.
     */
    fun <T> ifVisible(override: Boolean = false, viewOperation: () -> T) {
        val handler = Handler()

        if (isVisible) {
            if (override) {
                delayedViewOperation = null
            }
            handler.post {
                Timber.i("Executing view operation.")
                viewOperation()
            }
        } else {
            Timber.e("Fragment not visible yet.")
            delayedViewOperation = Runnable {
                if (isVisible) {
                    Timber.i("Executing delayed view operation.")
                    viewOperation()
                } else {
                    Timber.e("Fragment not visible after 16ms, aborting operation.")
                }
            }

            handler.postDelayed({
                if (delayedViewOperation != null) {
                    handler.post(delayedViewOperation)
                } else {
                    Timber.e("Overridden view operation; aborting.")
                }
            }, 16)
        }
    }

    abstract fun getFragmentTag(): String

    protected abstract fun inject() : Boolean

    protected abstract fun getPresenterImpl(): P

    protected abstract fun getLayoutId(): Int

    protected abstract fun getContentLayout(): ViewGroup

    protected abstract fun getSharedImage(): View?

    /**
     * Perform any necessary run-time setup of views: RecyclerViews, ClickListeners,
     * etc.
     */
    protected abstract fun configureViews()

    private fun attachHelper(activity: Activity?) {
        if (!injected) {
            inject()
            injected = true
        } else {
            Timber.e("${className()} already injected.")
        }

        if (created) {
            createHelper(null)
        }
    }

    private fun createHelper(savedInstanceState: Bundle?) {
        getPresenterImpl().onCreate(arguments, savedInstanceState, this as V)
    }
}
