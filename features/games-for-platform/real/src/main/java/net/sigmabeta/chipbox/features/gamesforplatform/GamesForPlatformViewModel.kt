package net.sigmabeta.chipbox.features.gamesforplatform

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation.toRoute
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.ui.list.ChipboxListViewModel
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

// SavedStateHandle isn't directly injectable in Metro the way it is in Hilt — it has to come
// out of `CreationExtras` at ViewModel resolution time. See `metrox-viewmodel` README for the
// `@AssistedFactory` + `ViewModelAssistedFactory` pattern this VM uses.
@AssistedInject
class GamesForPlatformViewModel(
    @Assisted savedStateHandle: SavedStateHandle,
    private val repository: Repository,
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<GamesForPlatformState>(
    GamesForPlatformState(),
    stringProvider,
    hatchet,
) {

    private val args: GamesForPlatform = savedStateHandle.toRoute()

    init {
        updateState { it.copy(platform = args.platform) }

        viewModelScope.launch {
            repository.getGamesForPlatform(args.platform).collect { data ->
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
                contentId = args.platform.ordinal.toLong(),
                startingPosition = startingPosition,
                shuffled = shuffled,
            )
        )
    }

    @AssistedFactory
    @ViewModelAssistedFactoryKey(GamesForPlatformViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): GamesForPlatformViewModel =
            create(extras.createSavedStateHandle())

        fun create(@Assisted savedStateHandle: SavedStateHandle): GamesForPlatformViewModel
    }

    private companion object {
        const val LOAD_OP = "games_for_platform.load"
    }
}
