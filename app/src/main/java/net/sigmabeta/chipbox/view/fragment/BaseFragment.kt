package net.sigmabeta.chipbox.view.fragment

import android.app.Activity
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import net.sigmabeta.chipbox.view.interfaces.FragmentContainer

abstract class BaseFragment : Fragment() {
    var injected: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)

        if (!injected) {
            inject()
            injected = true
        }

        (activity as FragmentContainer).setActivityTitle(getTitle())
    }

    fun showToastMessage(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    fun showErrorSnackbar(message: String, action: View.OnClickListener?, actionLabel: Int?) {
        val snackbar = Snackbar.make(getContentLayout(), message, Snackbar.LENGTH_LONG)

        if (action != null && actionLabel != null) {
            snackbar.setAction(actionLabel, action)
        }

        snackbar.show()
    }

    protected abstract fun inject()

    protected abstract fun getContentLayout(): FrameLayout

    protected abstract fun getTitle(): String
}
