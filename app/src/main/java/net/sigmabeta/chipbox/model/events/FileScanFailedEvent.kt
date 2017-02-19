package net.sigmabeta.chipbox.model.events

class FileScanFailedEvent(val reason: String) : PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String? {
        return reason
    }

    companion object {
        val EVENT_TYPE = "filescan.failed"
    }
}