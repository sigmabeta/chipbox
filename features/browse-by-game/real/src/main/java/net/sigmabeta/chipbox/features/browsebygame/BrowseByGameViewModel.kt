package net.sigmabeta.chipbox.features.browsebygame

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.ui.list.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@HiltViewModel
class BrowseByGameViewModel @Inject constructor(
    private val repository: Repository,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<BrowseByGameState>(
    BrowseByGameState(),
    stringProvider,
    hatchet,
) {

    init {
        viewModelScope.launch {
            repository.getAllGames().collect { data ->
                val lce: LCE<List<Game>> = when (data) {
                    Data.Loading -> LCE.Loading(LOAD_OP)
                    Data.Empty -> LCE.Content(emptyList())
                    is Data.Succeeded -> LCE.Content(data.data)
                    is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(data.message))
                }
                updateState { it.copy(games = lce) }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is BrowseByGameAction.GameClicked -> emit(NavigateTo(GameDetail(action.id)))
            else -> Unit
        }
    }

    private companion object {
        const val LOAD_OP = "browse_by_game.load"
    }
}
