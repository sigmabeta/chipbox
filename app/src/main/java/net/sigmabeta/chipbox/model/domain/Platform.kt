package net.sigmabeta.chipbox.model.domain

import net.sigmabeta.chipbox.model.domain.ListItem.Companion.CHANGE_ERROR

class Platform(val id: Long,
               val stringId: Int,
               val iconId: Int) : ListItem {
    override fun isTheSameAs(theOther: ListItem?): Boolean {
        if (theOther is Platform) {
            if (theOther.id == this.id) {
                return true
            }
        }

        return false
    }

    override fun hasSameContentAs(theOther: ListItem?): Boolean {
        if (theOther is Platform) {
            if (theOther.id == this.id) {
                return true
            }
        }

        return false
    }

    override fun getChangeType(theOther: ListItem?): Int {
        return CHANGE_ERROR
    }
}