package net.sigmabeta.chipbox.contentsource

/**
 * Indexes the available [ContentSource]s by id. A pure contract type — DI wiring lives in the
 * platform di modules (Android: AndroidFileContentSourceModule; JVM: JvmModules), so this stays
 * framework-free and multiplatform.
 */
class ContentSourceRegistry(
    sources: Set<ContentSource>,
) {
    private val byId = sources.associateBy { it.sourceId }
    fun get(sourceId: String): ContentSource? = byId[sourceId]
}
