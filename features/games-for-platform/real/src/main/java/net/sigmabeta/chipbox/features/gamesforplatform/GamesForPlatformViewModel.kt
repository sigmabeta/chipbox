package net.sigmabeta.chipbox.features.gamesforplatform

import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@AssistedInject
class GamesForPlatformViewModel(
    @Assisted private val platform: Platform,
    private val repository: Repository,
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<GamesForPlatformState>(
    GamesForPlatformState(),
    stringProvider,
    hatchet,
) {

    init {
        updateState { it.copy(platform = platform) }

        viewModelScope.launch {
            repository.getGamesForPlatform(platform).collect { data ->
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
            GamesForPlatformAction.PlayAllClicked -> startSession(startingPosition = 0)

            GamesForPlatformAction.ShuffleAllClicked ->
                startSession(startingPosition = 0, shuffled = true)

            is GamesForPlatformAction.GameClicked -> emit(NavigateTo(GameDetail(action.id)))

            else -> Unit
        }
    }

    private fun startSession(startingPosition: Int, shuffled: Boolean = false) {
        director.start(
            Session(
                type = SessionType.PLATFORM,
                contentId = platform.ordinal.toLong(),
                startingPosition = startingPosition,
                shuffled = shuffled,
            )
        )
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted platform: Platform): GamesForPlatformViewModel
    }

    private companion object {
        const val LOAD_OP = "games_for_platform.load"
    }
}
