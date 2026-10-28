package net.sigmabeta.chipbox.browserstate

import kotlinx.serialization.Serializable

/** One favorited id plus when it was favorited (newest-first ordering, refreshed on re-favorite). */
@Serializable
data class FavoriteEntry(
    val id: Long,
    val favoritedAtMs: Long,
)

/**
 * The favorites payload. Ids only — the library owns model data, so favorites survive a library
 * rebuild and unknown ids are dropped by the UI at hydration time.
 */
@Serializable
data class FavoritesState(
    val tracks: List<FavoriteEntry> = emptyList(),
    val games: List<FavoriteEntry> = emptyList(),
    val artists: List<FavoriteEntry> = emptyList(),
) {
    /** Dedupe by id (keeping the newest timestamp) and sort every list newest-first. */
    fun normalized(): FavoritesState = copy(
        tracks = tracks.normalizedEntries(),
        games = games.normalizedEntries(),
        artists = artists.normalizedEntries(),
    )
}

internal fun List<FavoriteEntry>.normalizedEntries(): List<FavoriteEntry> =
    groupBy { it.id }
        .map { (_, entries) -> entries.maxBy { it.favoritedAtMs } }
        .sortedWith(compareByDescending<FavoriteEntry> { it.favoritedAtMs }.thenByDescending { it.id })
