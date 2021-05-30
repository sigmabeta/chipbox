package net.sigmabeta.chipbox.navigation

import androidx.navigation.NavGraphBuilder
import net.sigmabeta.chipbox.features.game_detail.GameDetailArguments
import net.sigmabeta.chipbox.features.game_detail.GameDetailScreen
import net.sigmabeta.chipbox.features.game_detail.GameDetailViewModel
import net.sigmabeta.chipbox.features.games.GamesScreen
import net.sigmabeta.chipbox.features.games.GamesViewModel

fun NavGraphBuilder.destinations(navigateAction: (String) -> Unit) = listOf(
    NavDestination(
        "games",
        null,
        navigateAction,
        { GamesScreen(it as GamesViewModel, navigateAction) }
    ),
    NavDestination(
        "game",
        null,
        navigateAction,
        {
            val viewModel = it as GameDetailViewModel
//            val id = it.arguments?.getLong("gameId")!!
            viewModel.arguments = GameDetailArguments(4L)
            GameDetailScreen(viewModel)
        }
    )
)