package net.sigmabeta.chipbox.debug.real

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.GeneratorSource
import net.sigmabeta.chipbox.debug.ImageLoaderSource
import net.sigmabeta.chipbox.debug.RepositorySource
import net.sigmabeta.chipbox.debug.SpeakerSource
import net.sigmabeta.sage.storage.common.Storage

class RealDebugSettingsManager(private val storage: Storage) : DebugSettingsManager {
    override fun getShouldShowDebug(): Flow<Boolean> = storage
        .savedStringFlow(KEY_DEBUG_ENABLED)
        .map { it?.toBooleanStrictOrNull() ?: false }

    override fun setShouldShowDebug(value: Boolean) = storage.saveString(KEY_DEBUG_ENABLED, value.toString())

    override fun getRepositorySource(): Flow<RepositorySource> = storage
        .savedStringFlow(KEY_REPOSITORY_SOURCE)
        .map { RepositorySource.fromStorageValue(it) }

    override fun setRepositorySource(source: RepositorySource) =
        storage.saveString(KEY_REPOSITORY_SOURCE, source.name)

    override fun getGeneratorSource(): Flow<GeneratorSource> = storage
        .savedStringFlow(KEY_GENERATOR_SOURCE)
        .map { GeneratorSource.fromStorageValue(it) }

    override fun setGeneratorSource(source: GeneratorSource) =
        storage.saveString(KEY_GENERATOR_SOURCE, source.name)

    override fun getSpeakerSource(): Flow<SpeakerSource> = storage
        .savedStringFlow(KEY_SPEAKER_SOURCE)
        .map { SpeakerSource.fromStorageValue(it) }

    override fun setSpeakerSource(source: SpeakerSource) =
        storage.saveString(KEY_SPEAKER_SOURCE, source.name)

    override fun getImageLoaderSource(): Flow<ImageLoaderSource> = storage
        .savedStringFlow(KEY_IMAGE_LOADER_SOURCE)
        .map { ImageLoaderSource.fromStorageValue(it) }

    override fun setImageLoaderSource(source: ImageLoaderSource) =
        storage.saveString(KEY_IMAGE_LOADER_SOURCE, source.name)

    companion object {
        const val KEY_DEBUG_ENABLED = "setting.debug.enabled"
        const val KEY_REPOSITORY_SOURCE = "setting.debug.repository_source"
        const val KEY_GENERATOR_SOURCE = "setting.debug.generator_source"
        const val KEY_SPEAKER_SOURCE = "setting.debug.speaker_source"
        const val KEY_IMAGE_LOADER_SOURCE = "setting.debug.image_loader_source"
    }
}
