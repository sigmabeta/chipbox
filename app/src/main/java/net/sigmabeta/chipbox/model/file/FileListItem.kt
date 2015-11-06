package net.sigmabeta.chipbox.model.file

data class FileListItem(val type: Int, val filename: String, val path: String) : Comparable<FileListItem> {
    override fun compareTo(other: FileListItem): Int {
        if (other.type == type) {
            return filename.toLowerCase().compareTo(other.filename.toLowerCase())
        } else {
            if (type > other.type) {
                return 1
            } else {
                return -1
            }
        }
    }
}
