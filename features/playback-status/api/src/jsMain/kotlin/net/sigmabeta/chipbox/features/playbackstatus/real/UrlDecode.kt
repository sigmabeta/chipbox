package net.sigmabeta.chipbox.features.playbackstatus.real

// Best-effort ASCII percent-decode; this enforcement-only target never runs the debug screen.
internal actual fun urlDecodeUtf8(value: String): String {
    val sb = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val code = value.substring(i + 1, i + 3).toIntOrNull(radix = 16)
            if (code != null) {
                sb.append(code.toChar())
                i += 3
                continue
            }
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}
