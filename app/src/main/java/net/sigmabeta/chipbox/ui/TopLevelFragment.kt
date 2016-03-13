package net.sigmabeta.chipbox.ui

interface TopLevelFragment {
    fun isScrolledToBottom(): Boolean

    fun getTitle(): String
}