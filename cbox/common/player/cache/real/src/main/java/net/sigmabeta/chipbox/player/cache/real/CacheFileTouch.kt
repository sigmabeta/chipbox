package net.sigmabeta.chipbox.player.cache.real

import okio.Path

/**
 * JVM/Android `actual` for [touchLastModified]: okio exposes no portable set-mtime, so drop to
 * `java.io.File`. Lives in the JVM-shared source set, which both the android and jvm targets
 * inherit, so one `actual` covers both.
 */
internal actual fun touchLastModified(path: Path) {
    java.io.File(path.toString()).setLastModified(System.currentTimeMillis())
}
