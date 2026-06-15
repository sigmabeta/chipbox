package net.sigmabeta.chipbox.debug.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.GeneratorSource
import net.sigmabeta.chipbox.debug.ImageLoaderSource
import net.sigmabeta.chipbox.debug.RepositorySource
import net.sigmabeta.chipbox.debug.SpeakerSource

/**
 * Test [DebugSettingsManager] backed by [MutableStateFlow]s. The setters both write the flow AND
 * record the call so assertions can verify a ViewModel toggled the right value.
 */
class FakeDebugSettingsManager(
    initialShouldShowDebug: Boolean = false,
    initialRepositorySource: RepositorySource = RepositorySource.DEFAULT,
    initialGeneratorSource: GeneratorSource = GeneratorSource.DEFAULT,
    initialSpeakerSource: SpeakerSource = SpeakerSource.DEFAULT,
    initialImageLoaderSource: ImageLoaderSource = ImageLoaderSource.DEFAULT,
) : DebugSettingsManager {

    private val sink = MutableStateFlow(initialShouldShowDebug)
    private val repositorySource = MutableStateFlow(initialRepositorySource)
    private val generatorSource = MutableStateFlow(initialGeneratorSource)
    private val speakerSource = MutableStateFlow(initialSpeakerSource)
    private val imageLoaderSource = MutableStateFlow(initialImageLoaderSource)

    val setShouldShowDebugCalls: MutableList<Boolean> = mutableListOf()
    val setRepositorySourceCalls: MutableList<RepositorySource> = mutableListOf()
    val setGeneratorSourceCalls: MutableList<GeneratorSource> = mutableListOf()
    val setSpeakerSourceCalls: MutableList<SpeakerSource> = mutableListOf()
    val setImageLoaderSourceCalls: MutableList<ImageLoaderSource> = mutableListOf()

    override fun getShouldShowDebug(): Flow<Boolean> = sink.asStateFlow()
    override fun setShouldShowDebug(value: Boolean) {
        setShouldShowDebugCalls += value
        sink.value = value
    }

    override fun getRepositorySource(): Flow<RepositorySource> = repositorySource.asStateFlow()
    override fun setRepositorySource(source: RepositorySource) {
        setRepositorySourceCalls += source
        repositorySource.value = source
    }

    override fun getGeneratorSource(): Flow<GeneratorSource> = generatorSource.asStateFlow()
    override fun setGeneratorSource(source: GeneratorSource) {
        setGeneratorSourceCalls += source
        generatorSource.value = source
    }

    override fun getSpeakerSource(): Flow<SpeakerSource> = speakerSource.asStateFlow()
    override fun setSpeakerSource(source: SpeakerSource) {
        setSpeakerSourceCalls += source
        speakerSource.value = source
    }

    override fun getImageLoaderSource(): Flow<ImageLoaderSource> = imageLoaderSource.asStateFlow()
    override fun setImageLoaderSource(source: ImageLoaderSource) {
        setImageLoaderSourceCalls += source
        imageLoaderSource.value = source
    }
}
