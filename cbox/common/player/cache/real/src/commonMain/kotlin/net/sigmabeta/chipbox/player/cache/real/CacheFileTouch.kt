package net.sigmabeta.chipbox.player.cache.real

import okio.Path

/**
 * Bump a cache file's last-modified time, used by the [PcmCacheJanitor]'s LRU policy on every
 * cache hit. okio's [okio.FileSystem] can read mtime but not set it, so this is the one cache
 * operation that drops to a platform API — the `actual` lives in the JVM-shared source set.
 */
internal expect fun touchLastModified(path: Path)
