package net.sigmabeta.chipbox.view.interfaces

import android.view.View

interface BaseView {
    fun showToastMessage(message: String)

    fun showErrorSnackbar(message: String, action: View.OnClickListener?, actionLabel: Int?)
}
