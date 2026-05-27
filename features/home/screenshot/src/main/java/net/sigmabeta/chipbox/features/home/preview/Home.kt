package net.sigmabeta.chipbox.features.home.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.common.ui.components.api.NowPlayingHomeCardListModel
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.HomeSectionState
import net.sigmabeta.chipbox.features.home.HomeState
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.components.ListModel
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
private const val SOLO_ID = "solo_demo"
private const val SOLO_PRIORITY = 200
private const val SOLO_DATA_ID = -4001L
private const val NOW_PLAYING_ID = "now_playing"
private const val NOW_PLAYING_PRIORITY = 0

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
    return HomeState(
        sections = persistentListOf(
            // showHeader = false mirrors NowPlayingHomeModule's override — the card carries
            // its own title, so the section header would just duplicate it.
            section(NOW_PLAYING_ID, NOW_PLAYING_PRIORITY, "Now playing", nowPlayingItem(games.first()), showHeader = false),
            section(RANDOM_GAMES_ID, RANDOM_GAMES_PRIORITY, "Games of the day", gameCards(games)),
            section(SOLO_ID, SOLO_PRIORITY, "Featured", soloItem(games.first())),
            section(RNG_ID, RNG_PRIORITY, "RNG Take the Wheel", rngCards()),
        ),
    )
}

private fun nowPlayingItem(game: Game): ImmutableList<ListModel> = persistentListOf(
    NowPlayingHomeCardListModel(
        title = "Robotnik's Theme",
        artistsCaption = "Howard Drossin, Brad Buxer",
        artwork = SourceInfo(info = game.photoUrl),
        isPlaying = true,
        isBuffering = false,
        isError = false,
        progressFraction = 0.4f,
        clickAction = HomeAction.NowPlayingCardClicked,
        playPauseAction = HomeAction.NowPlayingPlayPauseClicked,
        appearAction = HomeAction.NowPlayingCardAppeared,
        disappearAction = HomeAction.NowPlayingCardDisappeared,
    ),
)

private fun homeLoadingState(): HomeState = HomeState(
    sections = persistentListOf(
        HomeSectionState(
            id = RANDOM_GAMES_ID,
            priority = RANDOM_GAMES_PRIORITY,
            lce = LCE.Loading(FakeModelGenerator().loadingName()),
        ),
    ),
)

private fun section(
    id: String,
    priority: Int,
    title: String,
    items: ImmutableList<ListModel>,
    showHeader: Boolean = true,
) = HomeSectionState(
    id = id,
    priority = priority,
    lce = LCE.Content(HomeModuleSection(title = title, items = items)),
    showHeader = showHeader,
)

private fun gameCards(games: List<Game>): ImmutableList<ListModel> = games.map { game ->
    gameCard(game.id + GAME_ID_OFFSET, game)
}.toImmutableList()

private fun soloItem(game: Game): ImmutableList<ListModel> = persistentListOf(
    gameCard(SOLO_DATA_ID, game),
)

private fun gameCard(dataId: Long, game: Game) = GridImageListModel(
    dataId = dataId,
    name = game.title,
    sourceInfo = game.photoUrl,
    imagePlaceholder = Icon.Album,
    clickAction = HomeAction.GameClicked(game.id),
    aspectRatio = GAME_COVER_ASPECT_RATIO,
)

private fun rngCards(): ImmutableList<ListModel> = persistentListOf(
    rngCard(RNG_SONG_DATA_ID, "Random Song", Icon.MusicNote, HomeAction.RandomSongClicked),
    rngCard(RNG_GAME_DATA_ID, "Random Game", Icon.Album, HomeAction.RandomGameClicked),
    rngCard(RNG_ARTIST_DATA_ID, "Random Artist", Icon.Person, HomeAction.RandomArtistClicked),
)

private fun rngCard(dataId: Long, name: String, icon: Icon, action: SageAction) = GridImageListModel(
    dataId = dataId,
    name = name,
    sourceInfo = null,
    imagePlaceholder = icon,
    clickAction = action,
)
