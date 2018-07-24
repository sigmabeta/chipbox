package net.sigmabeta.chipbox.model.domain

interface ListItem {
    fun isTheSameAs(theOther: ListItem?): Boolean

    fun hasSameContentAs(theOther: ListItem?): Boolean

    fun getChangeType(theOther: ListItem?): Int

    companion object {
        val CHANGE_ERROR = -1
    }
}