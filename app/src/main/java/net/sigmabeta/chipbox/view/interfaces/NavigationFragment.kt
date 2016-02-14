package net.sigmabeta.chipbox.view.interfaces

interface NavigationFragment {
    fun isScrolledToBottom(): Boolean

    fun getTitle(): String
}