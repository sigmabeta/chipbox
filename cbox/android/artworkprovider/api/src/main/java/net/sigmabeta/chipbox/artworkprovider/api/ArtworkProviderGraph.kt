package net.sigmabeta.chipbox.artworkprovider.api

import net.sigmabeta.chipbox.contentsource.AndroidFileContentSource
import net.sigmabeta.chipbox.repository.Repository

/**
 * Narrow interface that the app's [android.app.Application] implements so [ArtworkProvider]
 * can pull the two bindings it needs without depending on `apps/android` (which would be a
 * cycle: artworkprovider/api is a transitive dep of apps/android). Replaces Hilt's
 * `@EntryPoint` + `EntryPointAccessors.fromApplication(...)` pattern that the provider used
 * before M6 — see `docs/architecture/sage-integration.md`.
 *
 * The Application implementation reads these off its `ChipboxAppGraph` (Metro graph); this
 * file stays a pure interface so the artworkprovider/api module doesn't pick up the graph
 * type or a Metro runtime dep.
 */
interface ArtworkProviderGraph {
    fun repository(): Repository
    fun fileContentSource(): AndroidFileContentSource
}
