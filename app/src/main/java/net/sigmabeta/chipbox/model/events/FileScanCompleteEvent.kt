package net.sigmabeta.chipbox.model.events

class FileScanCompleteEvent(val newTracks: Int,
                            val updatedTracks: Int) : PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String? {
        return "Found $newTracks new tracks & updated $updatedTracks."
    }

    companion object {
        val EVENT_TYPE = "filescan.complete"
    }
}