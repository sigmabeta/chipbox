package net.sigmabeta.chipbox.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.ui.FragmentContainer

abstract class BaseFragment : Fragment(), BaseView {
    var injected: Boolean = false

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

    protected abstract fun inject()

    protected abstract fun getPresenter(): FragmentPresenter

    protected abstract fun getLayoutId(): Int

    protected abstract fun getContentLayout(): FrameLayout

    protected abstract fun getTitle(): String

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

        (activity as FragmentContainer).setActivityTitle(getTitle())
    }
}
