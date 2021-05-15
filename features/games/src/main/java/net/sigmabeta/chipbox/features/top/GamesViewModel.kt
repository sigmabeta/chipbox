package net.sigmabeta.chipbox.features.games

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Repository
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GamesViewModel @Inject constructor(
    private val repository: Repository
): ViewModel() {
    private val _games = MutableLiveData<List<Game>>()
    val games: LiveData<List<Game>> = _games

    init {
        Timber.v("Initializing Viewmodel.")

        viewModelScope.launch {
            val games = repository.getAllGames()
            onGamesLoaded(games)
        }
    }

    override fun onCleared() {
        viewModelScope.cancel()
    }

    private fun onGamesLoaded(games: List<Game>) {
        _games.value = games
    }
}