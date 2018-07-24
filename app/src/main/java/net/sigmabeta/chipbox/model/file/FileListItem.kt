package net.sigmabeta.chipbox.model.file

import net.sigmabeta.chipbox.model.domain.ListItem
import net.sigmabeta.chipbox.model.domain.ListItem.Companion.CHANGE_ERROR

data class FileListItem(val type: Int, val filename: String, val path: String) : Comparable<FileListItem>, ListItem {
    override fun compareTo(other: FileListItem): Int {
        if (other.type == type) {
            return filename.toLowerCase().compareTo(other.filename.toLowerCase())
        } else {
            if (type < other.type) {
                return 1
            } else {
                return -1
            }
        }
    }

    override fun isTheSameAs(theOther: ListItem?): Boolean {
        if (theOther is FileListItem) {
            if (theOther.path == this.path) {
                return true
            }
        }

        return false
    }

    override fun hasSameContentAs(theOther: ListItem?): Boolean {
        if (theOther is FileListItem) {
            if (theOther.filename == this.filename) {
                return true
            }
        }

        return false
    }

    override fun getChangeType(theOther: ListItem?): Int {
        if (theOther is FileListItem) {
            if (theOther.filename != this.filename) {
                return CHANGE_FILE
            }
        }

        return CHANGE_ERROR
    }

    companion object {
        val CHANGE_FILE = 1
    }
}
