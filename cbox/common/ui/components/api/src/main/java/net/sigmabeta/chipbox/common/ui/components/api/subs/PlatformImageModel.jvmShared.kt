package net.sigmabeta.chipbox.common.ui.components.api.subs

import okio.Path.Companion.toPath

/**
 * Shared by Android and desktop/JVM: rewrite an absolute local filesystem path into an okio
 * [okio.Path] so Coil loads it via [coil3.map.PathMapper] (literal path, no URI parsing) instead of
 * [coil3.map.StringMapper] (which would treat `#`/`?` in the path as URI syntax and truncate it).
 * Both targets can now hand out raw filesystem paths — desktop always does, and Android does when a
 * library location isn't a SAF tree — so both need the rewrite. URLs (network cover art) and SAF
 * `content://` URIs aren't absolute paths, so they pass through untouched for Coil's own mappers.
 */
internal actual fun platformImageModel(info: Any?): Any? =
    if (info is String && info.isAbsoluteLocalPath()) info.toPath() else info

private fun String.isAbsoluteLocalPath(): Boolean {
    if ("://" in this) return false // http(s)/file/content/jar/... URLs — let Coil parse the scheme.
    return startsWith('/') || WINDOWS_ABSOLUTE_PATH.matches(this)
}

private val WINDOWS_ABSOLUTE_PATH = Regex("""^[A-Za-z]:[\\/].*""")
