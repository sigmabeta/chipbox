package net.sigmabeta.chipbox.contentsource

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.sage.di.AppScope

@SingleIn(AppScope::class)
class ContentSourceRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards ContentSource>,
) {
    private val byId = sources.associateBy { it.sourceId }
    fun get(sourceId: String): ContentSource? = byId[sourceId]
}
