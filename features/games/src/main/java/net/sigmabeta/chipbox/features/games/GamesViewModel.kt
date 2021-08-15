package net.sigmabeta.chipbox.features.games

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Inject

@HiltViewModel
class GamesViewModel @Inject constructor(
    private val repository: Repository
): ViewModel() {
    @OptIn(FlowPreview::class)
    fun gamesData() = repository
        .getAllGames(withArtists = true)
        .sample(66L)
}