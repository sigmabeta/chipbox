package net.sigmabeta.chipbox.ui

import android.view.View
import net.sigmabeta.chipbox.ChipboxApplication

interface BaseView {

    fun showToastMessage(message: String)

    fun showErrorSnackbar(message: String, action: View.OnClickListener?, actionLabel: Int?)

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

    fun showInvalidClearError(error: InvalidClearViewException)
}
