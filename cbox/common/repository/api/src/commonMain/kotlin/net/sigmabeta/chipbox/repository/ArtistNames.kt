package net.sigmabeta.chipbox.repository

/**
 * The delimiters a track's `artist` string is split on to derive its credited artists
 * (e.g. `"Akari Kaida, Yoshino Aoki"` → two artists). Both repository implementations split on this
 * one pattern, and any consumer that mirrors the derivation (e.g. the device benchmark choosing an
 * artist to open) shares it, so the rule cannot drift between them.
 */
val ARTIST_NAME_DELIMITERS = Regex(", &|,| or | and |&")
