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

    fun getImageLoaderSource(): Flow<ImageLoaderSource>
    fun setImageLoaderSource(source: ImageLoaderSource)

    fun getFavoritesSource(): Flow<FavoritesSource>
    fun setFavoritesSource(source: FavoritesSource)

    fun getHistorySource(): Flow<HistorySource>
    fun setHistorySource(source: HistorySource)

    fun getPlaylistsSource(): Flow<PlaylistsSource>
    fun setPlaylistsSource(source: PlaylistsSource)
}
