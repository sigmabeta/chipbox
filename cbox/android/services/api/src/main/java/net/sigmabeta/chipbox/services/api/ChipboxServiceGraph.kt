package net.sigmabeta.chipbox.services.api

import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.sage.logging.Hatchet

/**
 * Narrow interface that the app's [android.app.Application] implements so
 * [ChipboxPlaybackService] can pull its constructor-shaped deps without depending on
 * `apps/android` (which would be a cycle: services/api is a transitive dep of apps/android).
 * Replaces Hilt's `@AndroidEntryPoint` + `@Inject lateinit var` member-injection pattern the
 * service used before M6 — see `docs/architecture/sage-integration.md`.
 *
 * Implementation reads accessors off `ChipboxAppGraph` (Metro graph); this file stays a pure
 * interface so the services/api module doesn't pick up the graph type or a Metro runtime dep.
 */
interface ChipboxServiceGraph {
    fun libraryBrowser(): LibraryBrowser
    fun director(): Director
    fun hatchet(): Hatchet
}
