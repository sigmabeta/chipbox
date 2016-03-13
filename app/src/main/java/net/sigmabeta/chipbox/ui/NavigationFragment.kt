package net.sigmabeta.chipbox.ui

interface NavigationFragment {
    fun isScrolledToBottom(): Boolean

    fun getTitle(): String
}