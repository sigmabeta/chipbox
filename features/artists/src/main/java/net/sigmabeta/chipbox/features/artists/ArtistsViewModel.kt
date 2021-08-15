package net.sigmabeta.chipbox.features.artists

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Inject

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    private val repository: Repository
): ViewModel() {
    @OptIn(FlowPreview::class)
    fun artistsData() = repository
        .getAllArtists()
        .sample(66L)
}