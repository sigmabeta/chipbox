package net.sigmabeta.chipbox.features.playbackstatus.real

import java.net.URLDecoder

internal actual fun urlDecodeUtf8(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)
