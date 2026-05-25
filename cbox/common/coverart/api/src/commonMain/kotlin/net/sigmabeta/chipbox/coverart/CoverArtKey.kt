package net.sigmabeta.chipbox.coverart

import net.sigmabeta.chipbox.models.Platform

/**
 * Shared key for cover-art lookups and overrides: a game's title plus its platform set, which
 * together determine which IGDB cover applies. Used by both the cache and the overrides store so an
 * override lines up with the cache entry it supersedes.
 */
fun coverArtKey(title: String, platforms: Set<Platform>): String =
    title + "|" + platforms.map { it.name }.sorted().joinToString(",")

/**
 * How long a recorded cover-art match stays valid before it's re-queried, in days. Applies both to
 * the cache JSON and to the date inside a folder's igdb.txt, so a match that hasn't been confirmed
 * against IGDB in this long is looked up again — picking up covers IGDB has added since.
 */
const val COVER_ART_TTL_DAYS: Long = 180L
