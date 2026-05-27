package net.sigmabeta.chipbox.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class HomeViewModel @Inject constructor(
    modules: Set<HomeModule>,
    private val repository: Repository,
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<HomeState>(
    HomeState(
        sections = modules
            .sortedBy { it.priority }
            .map { HomeSectionState(it.id, it.priority, LCE.Uninitialized) }
            .toImmutableList(),
    ),
    stringProvider,
    hatchet,
) {

    init {
        // Each module patches only its own slot, so modules render independently as their
        // data arrives — no `combine` / `flatMapMerge` orchestration needed.
        modules.forEach { module ->
            viewModelScope.launch {
                module.state().collect { lce ->
                    updateState { state ->
                        state.copy(
                            sections = state.sections.map { section ->
                                if (section.id == module.id) section.copy(lce = lce) else section
                            }.toImmutableList(),
                        )
                    }
                }
            }
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is HomeAction.GameClicked -> emit(NavigateTo(GameDetail(action.id)))
            HomeAction.RandomSongClicked -> playRandomSong()
            HomeAction.RandomGameClicked -> navigateToRandomGame()
            HomeAction.RandomArtistClicked -> navigateToRandomArtist()
            else -> Unit
        }
    }

    private fun playRandomSong() = viewModelScope.launch {
        val pick = repository.getAllTracks(withGame = false, withArtists = false)
            .firstSucceeded()?.randomOrNull() ?: return@launch
        director.start(Session(type = SessionType.SINGLE_TRACK, contentId = pick.id))
    }

    private fun navigateToRandomGame() = viewModelScope.launch {
        val pick = repository.getAllGames(withTracks = false, withArtists = false)
            .firstSucceeded()?.randomOrNull() ?: return@launch
        emit(NavigateTo(GameDetail(pick.id)))
    }

    private fun navigateToRandomArtist() = viewModelScope.launch {
        val pick = repository.getAllArtists(withTracks = false, withGames = false)
            .firstSucceeded()?.randomOrNull() ?: return@launch
        emit(NavigateTo(ArtistDetail(pick.id)))
    }
}

/**
 * Drains the receiver flow up to the first non-Loading emission and returns its payload —
 * `null` on `Data.Empty`/`Data.Failed`. Repository flows are cold, so the lookup is fresh
 * each call: the RNG click-action contract is "re-randomize on every tap".
 */
private suspend fun <T> Flow<Data<T>>.firstSucceeded(): T? {
    val terminal = firstOrNull { it !is Data.Loading } ?: return null
    return (terminal as? Data.Succeeded<T>)?.data
}
