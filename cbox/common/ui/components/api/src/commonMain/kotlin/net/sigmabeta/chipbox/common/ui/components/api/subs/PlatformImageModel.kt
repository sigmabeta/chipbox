package net.sigmabeta.chipbox.common.ui.components.api.subs

/**
 * Normalise a stored image reference into a model Coil can load on this platform.
 *
 * Most values pass through unchanged. The point of the seam is the JVM/Android actual: a game's
 * cover-art reference can be a raw absolute filesystem path (desktop's `LocalFileContentSource`
 * stores `path.toString()`, and Android does the same when a library location isn't a SAF tree),
 * and Coil's [coil3.map.StringMapper] runs any `String` through `String.toUri()`. That parser
 * treats `#` (and `?`) as URI syntax, so a cover whose folder or filename contains `#` — e.g.
 * `…/Homicides_#1_(Sharp_X68000)/…` — has its path truncated at the `#` and fails to load. Handing
 * Coil an okio `Path` instead routes through [coil3.map.PathMapper], which builds the file `Uri`
 * from the literal path with no parsing, so `#`/`?`/`%` survive. See [CrossfadeImage].
 */
internal expect fun platformImageModel(info: Any?): Any?
