package net.sigmabeta.chipbox.features.game_detail

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Inject

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    private val repository: Repository,
    private val director: Director
) : ViewModel() {
    var arguments: GameDetailArguments? = null

    @OptIn(FlowPreview::class)
    fun gameData() = repository
        .getGame(arguments?.id!!, withArtists = true, withTracks = true)
        .sample(66L)

    fun onTrackClick(position: Int) {
        director.start(
            Session(
                SessionType.GAME,
                arguments?.id!!,
                position,
                -1
            )
        )
    }
}