package net.sigmabeta.chipbox.contentsource

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentSourceRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards ContentSource>,
) {
    private val byId = sources.associateBy { it.sourceId }
    fun get(sourceId: String): ContentSource? = byId[sourceId]
}
