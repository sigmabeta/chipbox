package net.sigmabeta.chipbox.ui

import android.view.View

interface BaseView {
    fun showToastMessage(message: String)

    fun showErrorSnackbar(message: String, action: View.OnClickListener?, actionLabel: Int?)

    fun onClick(clicked: View)
}
