package net.sigmabeta.chipbox.features.game_detail

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Inject

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    private val repository: Repository
) : ViewModel() {
    var arguments: GameDetailArguments? = null

    @OptIn(FlowPreview::class)
    fun gameData() = repository
        .getGame(arguments?.id!!)
        .sample(66L)
}