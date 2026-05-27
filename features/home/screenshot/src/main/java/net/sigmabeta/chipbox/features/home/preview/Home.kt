package net.sigmabeta.chipbox.features.home.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.HomeSectionState
import net.sigmabeta.chipbox.features.home.HomeState
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.list.WidthClass
import net.sigmabeta.sage.ui.Icon

private const val RANDOM_GAMES_ID = "random_games"
private const val RANDOM_GAMES_PRIORITY = 100
private const val GAME_COVER_ASPECT_RATIO = 0.75f
private const val GAME_ID_OFFSET = 3_000_000_000L
private const val RNG_ID = "rng_take_the_wheel"
private const val RNG_PRIORITY = 1000
private const val RNG_SONG_DATA_ID = -1001L
private const val RNG_GAME_DATA_ID = -1002L
private const val RNG_ARTIST_DATA_ID = -1003L

@DevicePreviews
@Composable
internal fun Home(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = homeState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun HomeLoading(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = homeLoadingState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun homeState(): HomeState {
    val games = FakeModelGenerator().randomGames()
    val gameCards = games.map { game ->
        GridImageListModel(
            dataId = game.id + GAME_ID_OFFSET,
            name = game.title,
            sourceInfo = game.photoUrl,
            imagePlaceholder = Icon.Album,
            clickAction = HomeAction.GameClicked(game.id),
            aspectRatio = GAME_COVER_ASPECT_RATIO,
        )
    }.toImmutableList()
    val rngCards = persistentListOf(
        GridImageListModel(
            dataId = RNG_SONG_DATA_ID,
            name = "Random Song",
            sourceInfo = null,
            imagePlaceholder = Icon.MusicNote,
            clickAction = HomeAction.RandomSongClicked,
        ),
        GridImageListModel(
            dataId = RNG_GAME_DATA_ID,
            name = "Random Game",
            sourceInfo = null,
            imagePlaceholder = Icon.Album,
            clickAction = HomeAction.RandomGameClicked,
        ),
        GridImageListModel(
            dataId = RNG_ARTIST_DATA_ID,
            name = "Random Artist",
            sourceInfo = null,
            imagePlaceholder = Icon.Person,
            clickAction = HomeAction.RandomArtistClicked,
        ),
    )
    return HomeState(
        sections = persistentListOf(
            HomeSectionState(
                id = RANDOM_GAMES_ID,
                priority = RANDOM_GAMES_PRIORITY,
                lce = LCE.Content(
                    HomeModuleSection(title = "Games of the day", items = gameCards),
                ),
            ),
            HomeSectionState(
                id = RNG_ID,
                priority = RNG_PRIORITY,
                lce = LCE.Content(
                    HomeModuleSection(title = "RNG Take the Wheel", items = rngCards),
                ),
            ),
        ),
    )
}

private fun homeLoadingState(): HomeState = HomeState(
    sections = persistentListOf(
        HomeSectionState(
            id = RANDOM_GAMES_ID,
            priority = RANDOM_GAMES_PRIORITY,
            lce = LCE.Loading(FakeModelGenerator().loadingName()),
        ),
    ),
)
