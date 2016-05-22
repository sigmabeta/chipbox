package net.sigmabeta.chipbox.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import com.squareup.picasso.Callback
import net.sigmabeta.chipbox.util.logError

abstract class BaseFragment : Fragment(), BaseView, View.OnClickListener {
    var injected: Boolean = false

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

    fun setActivityTitle(title: String) {
        (activity as FragmentContainer).setTitle(title)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        getPresenter().onCreate(arguments, savedInstanceState, this)
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
        getPresenter().onResume()
    }

    override fun onPause() {
        super.onPause()
        getPresenter().onPause()
    }

    override fun onDestroy() {
        super.onDestroy()

        val ending = activity.isFinishing || isRemoving
        getPresenter().onDestroy(ending)
    }

    override fun startTransition() {
        activity?.startPostponedEnterTransition()
    }

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

    override fun onClick(clicked: View) {
        getPresenter().onClick(clicked.id)
    }

    /**
     * Performs an operation (probably a view operation of some sort) only if this
     * Fragment is visible to the user.
     */
    fun <T> ifVisible(viewOperation: () -> T) {
       if (isVisible) viewOperation()
    }

    open fun getShareableViews(): Array<Pair<View, String>>? = null

    protected abstract fun inject()

    protected abstract fun getPresenter(): FragmentPresenter

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
        }
    }
}
