package net.sigmabeta.chipbox.features.artist_detail

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val repository: Repository
) : ViewModel() {
    var arguments: ArtistDetailArguments? = null

    @OptIn(FlowPreview::class)
    fun artistData() = repository
        .getArtist(arguments?.id!!, withTracks = true)
        .sample(66L)
}