package net.sigmabeta.chipbox.model.events

class FileScanEvent(val type: Int,
                    val name: String,
                    val count: Int = 0) : PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String? {
        return name
    }

    companion object {
        val EVENT_TYPE = "filescan"

        val TYPE_FOLDER = 0
        val TYPE_NEW_TRACK = 1
        val TYPE_BAD_TRACK = 2
        val TYPE_NEW_MULTI_TRACK = 3
        val TYPE_UPDATED_TRACK = 4
        val TYPE_DELETED_TRACK = 5
    }
}
