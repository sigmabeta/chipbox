package net.sigmabeta.chipbox.ui

import android.view.View

interface NavigationFragment {
    fun isScrolledToBottom(): Boolean

    fun getScrollingView(): View
}