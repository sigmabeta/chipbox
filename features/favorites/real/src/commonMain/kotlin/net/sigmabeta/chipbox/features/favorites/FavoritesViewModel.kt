package net.sigmabeta.chipbox.features.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent.NavigateTo
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListViewModel
import net.sigmabeta.chipbox.favorites.FavoritesRepository
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class FavoritesViewModel @Inject constructor(
    private val repository: Repository,
    private val favorites: FavoritesRepository,
    private val director: Director,
    stringProvider: StringProvider,
    hatchet: Hatchet,
) : ChipboxListViewModel<FavoritesState>(
    FavoritesState(),
    stringProvider,
    hatchet,
) {

    init {
        // Each section joins the favorite-id stream with the full library list, then orders the
        // hydrated models by favorite recency (the id stream is newest-first) and drops ids whose
        // library row no longer exists. The director resolves the FAVORITES session the same way,
        // so a tapped row's position lines up with what plays.
        viewModelScope.launch {
            combine(favorites.favoriteTrackIds(), repository.getAllTracks(withGame = true)) { ids, data ->
                hydrate(ids, data) { tracks: List<Track> -> tracks.associateBy { it.id } }
            }.collect { lce -> updateState { it.copy(tracks = lce) } }
        }

        viewModelScope.launch {
            combine(favorites.favoriteGameIds(), repository.getAllGames()) { ids, data ->
                hydrate(ids, data) { games: List<Game> -> games.associateBy { it.id } }
            }.collect { lce -> updateState { it.copy(games = lce) } }
        }

        viewModelScope.launch {
            combine(favorites.favoriteArtistIds(), repository.getAllArtists()) { ids, data ->
                hydrate(ids, data) { artists: List<Artist> -> artists.associateBy { it.id } }
            }.collect { lce -> updateState { it.copy(artists = lce) } }
        }

        viewModelScope.launch {
            director.metadataState().collect { track ->
                updateState { it.copy(playingTrackId = track?.id) }
            }
        }
    }

    /**
     * Map a (favorite-ids, library-data) pair into an [LCE] of the hydrated favorites, in favorite
     * order. While the library is still loading, surface a real [LCE.Loading] only when there are
     * favorites to load — an empty id list collapses straight to empty content so the screen's
     * "nothing favorited" state shows immediately.
     */
    private inline fun <reified T> hydrate(
        ids: List<Long>,
        data: Data<List<T>>,
        byId: (List<T>) -> Map<Long, T>,
    ): LCE<List<T>> = when (data) {
        Data.Loading -> if (ids.isEmpty()) LCE.Content(emptyList()) else LCE.Loading(LOAD_OP)

        Data.Empty -> LCE.Content(emptyList())

        is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(data.message))

        is Data.Succeeded -> {
            val lookup = byId(data.data)
            LCE.Content(ids.mapNotNull { lookup[it] })
        }
    }

    override fun handleAction(action: SageAction) {
        when (action) {
            is FavoritesAction.TrackClicked -> startSession(startingPosition = action.position)
            is FavoritesAction.GameClicked -> emit(NavigateTo(GameDetail(action.id)))
            is FavoritesAction.ArtistClicked -> emit(NavigateTo(ArtistDetail(action.id)))
            else -> Unit
        }
    }

    private fun startSession(startingPosition: Int, shuffled: Boolean = false) {
        // Hand the director the exact track list the screen is showing as the session's explicit
        // setlist, so [startingPosition] lines up with the tapped row and the director needs no
        // dependency on the favorites store. No favorites loaded yet → nothing to start.
        val trackIds = (state.value.tracks as? LCE.Content)?.data?.map { it.id }
        if (trackIds.isNullOrEmpty()) return
        val session = Session(
            type = SessionType.FAVORITES,
            contentId = 0L,
            explicitSetlist = trackIds,
            startingPosition = startingPosition,
            shuffled = shuffled,
        )
        director.request(SessionRequest.Start(session))
    }

    private companion object {
        const val LOAD_OP = "favorites.load"
    }
}
