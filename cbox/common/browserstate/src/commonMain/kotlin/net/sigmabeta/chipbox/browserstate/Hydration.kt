package net.sigmabeta.chipbox.browserstate

/**
 * Map ids to hydrated models, dropping ids the current library no longer knows. RSN extraction
 * re-keys tracks and a rebuilt library can lose ids; favorites/playlists/history store ids, so
 * every UI hydration path must tolerate misses instead of erroring.
 */
fun <T> hydrateIds(ids: List<Long>, resolve: (Long) -> T?): List<T> = ids.mapNotNull(resolve)
