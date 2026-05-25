package net.sigmabeta.chipbox.player.cache.real

import okio.Path

/**
 * JS has no portable set-mtime, and the PCM cache only runs on JVM/Android, so the LRU freshness
 * touch is a no-op on this (enforcement-only) target.
 */
internal actual fun touchLastModified(path: Path) = Unit
