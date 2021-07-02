package net.sigmabeta.chipbox.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NamedNavArgument
import androidx.navigation.compose.navArgument
import net.sigmabeta.chipbox.features.artist_detail.ArtistDetailArguments
import net.sigmabeta.chipbox.features.artist_detail.ArtistDetailScreen
import net.sigmabeta.chipbox.features.artist_detail.ArtistDetailViewModel
import net.sigmabeta.chipbox.features.artists.ArtistsScreen
import net.sigmabeta.chipbox.features.artists.ArtistsViewModel
import net.sigmabeta.chipbox.features.game_detail.GameDetailArguments
import net.sigmabeta.chipbox.features.game_detail.GameDetailScreen
import net.sigmabeta.chipbox.features.game_detail.GameDetailViewModel
import net.sigmabeta.chipbox.features.games.GamesScreen
import net.sigmabeta.chipbox.features.games.GamesViewModel

fun destinations(navigateAction: (String) -> Unit) = listOf(
    NavDestination(
        "games",
        noArguments(),
        { vm, _ -> GamesScreen(vm as GamesViewModel, navigateAction) },
        { hiltViewModel<GamesViewModel>() }
    ),
    NavDestination(
        "artists",
        noArguments(),
        { vm, _ -> ArtistsScreen(vm as ArtistsViewModel, navigateAction) },
        { hiltViewModel<ArtistsViewModel>() }
    ),
    NavDestination(
        "game/{gameId}",
        listOf(navArgument("gameId") { type = NavType.LongType }),
        { vm, args ->
            val viewModel = vm as GameDetailViewModel
            val id = args?.getLong("gameId")!!
            viewModel.arguments = GameDetailArguments(id)
            GameDetailScreen(viewModel)
        },
        { hiltViewModel<GameDetailViewModel>() }
    ),
    NavDestination(
        "artist/{artistId}",
        listOf(navArgument("artistId") { type = NavType.LongType }),
        { vm, args ->
            val viewModel = vm as ArtistDetailViewModel
            val id = args?.getLong("artistId")!!
            viewModel.arguments = ArtistDetailArguments(id)
            ArtistDetailScreen(viewModel)
        },
        { hiltViewModel<ArtistDetailViewModel>() }
    )
)

fun noArguments() = emptyList<NamedNavArgument>()