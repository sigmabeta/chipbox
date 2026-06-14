package net.sigmabeta.chipbox.debug

import kotlinx.coroutines.flow.Flow

interface DebugSettingsManager {
    fun getShouldShowDebug(): Flow<Boolean>
    fun setShouldShowDebug(value: Boolean)

    fun getRepositorySource(): Flow<RepositorySource>
    fun setRepositorySource(source: RepositorySource)

    fun getGeneratorSource(): Flow<GeneratorSource>
    fun setGeneratorSource(source: GeneratorSource)

    fun getSpeakerSource(): Flow<SpeakerSource>
    fun setSpeakerSource(source: SpeakerSource)
}
