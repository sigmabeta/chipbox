package net.sigmabeta.chipbox.model.events

class FileScanFailedEvent(val exception: Throwable) : PlaybackEvent() {
    override fun getType(): String {
        return EVENT_TYPE
    }

    override fun getDataAsString(): String? {
        return exception.message
    }

    companion object {
        val EVENT_TYPE = "filescan.failed"
    }
}