package net.sigmabeta.chipbox.model.events

class FileScanEvent(val type: Int,
                    val name: String) : PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String? {
        return name
    }

    companion object {
        val EVENT_TYPE = "filescan"

        val TYPE_FOLDER = 0
        val TYPE_TRACK = 1
        val TYPE_BAD_TRACK = 2
    }
}
