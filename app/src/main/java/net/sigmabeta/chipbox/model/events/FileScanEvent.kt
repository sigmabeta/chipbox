package net.sigmabeta.chipbox.model.events

class FileScanEvent(val path: String?,
                    val file: String?) {

    override fun toString(): String {
        return "Event -> Path:${path} | File: ${file}"
    }
}
