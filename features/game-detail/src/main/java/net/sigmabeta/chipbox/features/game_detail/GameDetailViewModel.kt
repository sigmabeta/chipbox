package net.sigmabeta.chipbox.features.game_detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Inject

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    private val repository: Repository
) : ViewModel() {
    var arguments: GameDetailArguments? = null
        set(value) {
            if (value != null) {
                field = value
                loadGameDetail(value.id)
            } else {
                onLoadError()
            }
        }

    private val _game = MutableLiveData<Game>()

    val game: LiveData<Game> = _game

    override fun onCleared() {
        viewModelScope.cancel()
    }

    private fun loadGameDetail(id: Long) {
        viewModelScope.launch {
            val game = repository.getGame(id)
            if (game != null) {
                onGameDetailLoaded(game)
            } else {
                onLoadError()
            }
        }
    }

    private fun onGameDetailLoaded(game: Game) {
        _game.value = game
    }

    private fun onLoadError() {
        TODO("Not yet implemented")
    }
}