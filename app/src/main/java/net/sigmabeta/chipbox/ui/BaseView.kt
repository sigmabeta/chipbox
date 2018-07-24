package net.sigmabeta.chipbox.ui

import android.view.View
import net.sigmabeta.chipbox.ChipboxApplication

interface BaseView {
    fun onClick(clicked: View)

    fun getTypedApplication(): ChipboxApplication

    fun requestReSetup()

    fun showErrorState()

    fun showEmptyState()

    fun showLoadingState()

    fun showContent()

    /**
     * Error Handling
     */

    fun showError(message: String, action: View.OnClickListener? = null, actionLabel: Int = 0)
}
