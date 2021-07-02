package net.sigmabeta.chipbox.features.artists

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.repository.Repository
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    private val repository: Repository
): ViewModel() {
    private val _artists = MutableLiveData<List<Artist>>()
    val artists: LiveData<List<Artist>> = _artists

    init {
        Timber.v("Creating ArtistsViewModel")
        viewModelScope.launch {
            val artists = repository.getAllArtists()
            onArtistsLoaded(artists)
        }
    }

    override fun onCleared() {
        viewModelScope.cancel()
    }

    private fun onArtistsLoaded(artists: List<Artist>) {
        _artists.value = artists
    }
}