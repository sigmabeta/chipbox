package net.sigmabeta.chipbox.features.artist_detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val repository: Repository
) : ViewModel() {
    var arguments: ArtistDetailArguments? = null
        set(value) {
            if (value != null) {
                field = value
                loadArtistDetail(value?.id)
            } else {
                onLoadError()
            }
        }

    private val _artist = MutableLiveData<Artist>()

    val artist: LiveData<Artist> = _artist

    override fun onCleared() {
        viewModelScope.cancel()
    }

    private fun loadArtistDetail(id: Long) {
        viewModelScope.launch {
            val artist = repository.getArtist(id)
            if (artist != null) {
                onArtistDetailLoaded(artist)
            } else {
                onLoadError()
            }
        }
    }

    private fun onArtistDetailLoaded(artist: Artist) {
        _artist.value = artist
    }

    private fun onLoadError() {
        TODO("Not yet implemented")
    }
}