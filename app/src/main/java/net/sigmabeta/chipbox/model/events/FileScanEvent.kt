package net.sigmabeta.chipbox.model.events

class FileScanEvent(val type: Int,
                    val name: String) {
    companion object {
        val TYPE_FOLDER = 0
        val TYPE_TRACK = 1
        val TYPE_BAD_TRACK = 2
    }
}
