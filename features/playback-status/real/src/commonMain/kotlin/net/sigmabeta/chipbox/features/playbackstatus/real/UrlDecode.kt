package net.sigmabeta.chipbox.features.playbackstatus.real

/**
 * UTF-8 percent-decode of a content-tree path for the debug "shorten path" display. JVM/Android
 * use java.net.URLDecoder (unchanged); the enforcement-only JS target uses a best-effort manual
 * decoder. Callers wrap this in runCatching, so a decode failure falls back to the raw path.
 */
internal expect fun urlDecodeUtf8(value: String): String
