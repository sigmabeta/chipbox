package net.sigmabeta.chipbox.features.home.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.common.ui.components.api.NowPlayingHomeCardListModel
import net.sigmabeta.chipbox.common.ui.components.api.ScanCardStatus
import net.sigmabeta.chipbox.common.ui.components.api.ScanStatusCardListModel
import net.sigmabeta.chipbox.common.ui.components.api.ScanStatusDetail
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.HomeSectionState
import net.sigmabeta.chipbox.features.home.HomeState
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.list.WidthClass
import net.sigmabeta.sage.ui.Icon

private const val GAME_OF_THE_DAY_ID = "game_of_the_day"
private const val GAME_OF_THE_DAY_PRIORITY = 100
private const val GAME_COVER_ASPECT_RATIO = 0.75f
private const val GAME_OF_THE_DAY_MAX_WIDTH_DP = 400f
private const val GAME_ID_OFFSET = 3_000_000_000L
private const val RECENTLY_ADDED_ID = "recently_added_games"
private const val RECENTLY_ADDED_PRIORITY = 150
private const val RECENTLY_ADDED_GAME_OFFSET = 8_000_000_000L
private const val RECENTLY_PLAYED_ID = "recently_played_games"
private const val RECENTLY_PLAYED_PRIORITY = 200
private const val RECENTLY_PLAYED_GAME_OFFSET = 4_000_000_000L
private const val MOST_PLAYED_SONGS_ID = "most_played_songs"
private const val MOST_PLAYED_SONGS_PRIORITY = 300
private const val SONG_ID_OFFSET = 5_000_000_000L
private const val MOST_PLAYED_ARTISTS_ID = "most_played_artists"
private const val MOST_PLAYED_ARTISTS_PRIORITY = 500
private const val ARTIST_ID_OFFSET = 7_000_000_000L
private const val PREVIEW_SCROLLER_COUNT = 6
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

@DevicePreviews
@Composable
internal fun HomeEmptyNoFolders(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = HomeState(libraryFolderCount = 0, hasTracks = false),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun HomeEmptyFoldersPresent(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = HomeState(libraryFolderCount = 1, hasTracks = false),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun HomeScanStatusScanning(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = scanStatusState(ScanCardStatus.SCANNING),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun HomeScanStatusComplete(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = scanStatusState(ScanCardStatus.COMPLETE),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun HomeScanStatusFailed(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = scanStatusState(ScanCardStatus.FAILED),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun scanStatusState(status: ScanCardStatus): HomeState {
    val detail: ScanStatusDetail = when (status) {
        ScanCardStatus.SCANNING -> ScanStatusDetail.Rows(
            persistentListOf(
                scanChangeRow(1L, "Chrono Trigger", "Added · 60 tracks"),
                scanChangeRow(2L, "Mega Man 2", "Updated · 11 tracks"),
                scanChangeRow(3L, "Final Fantasy VI", "Added · 62 tracks"),
                scanChangeRow(4L, "Castlevania", "Added · 18 tracks"),
            ),
        )

        ScanCardStatus.COMPLETE -> ScanStatusDetail.Rows(
            persistentListOf(
                scanSummaryRow("Elapsed (seconds)", "42"),
                scanSummaryRow("Games found", "37"),
                scanSummaryRow("Tracks found", "1024"),
                scanSummaryRow("Tracks failed", "3"),
            ),
        )

        ScanCardStatus.FAILED -> ScanStatusDetail.Error(
            "The scan failed while reading bad-folder/track.spc.",
        )
    }
    val card = ScanStatusCardListModel(
        status = status,
        statusLabel = when (status) {
            ScanCardStatus.SCANNING -> "Library Scan Scanning"
            ScanCardStatus.COMPLETE -> "Library Scan Complete"
            ScanCardStatus.FAILED -> "Library Scan Failed"
        },
        currentFile = "robotnik_theme.spc".takeIf { status == ScanCardStatus.SCANNING },
        detail = detail,
        dismissAction = HomeAction.ScanStatusDismissed.takeIf { status != ScanCardStatus.SCANNING },
    )
    return HomeState(
        sections = persistentListOf(
            HomeSectionState(
                id = "scan_status",
                priority = 50,
                lce = LCE.Content(HomeModuleSection(title = "Scan", items = persistentListOf(card))),
                showHeader = false,
            ),
        ),
        hasTracks = true,
    )
}

private fun scanChangeRow(dataId: Long, name: String, caption: String) = ImageNameCaptionListModel(
    dataId = dataId,
    name = name,
    caption = caption,
    sourceInfo = SourceInfo(info = null),
    imagePlaceholder = Icon.Album,
    clickAction = HomeAction.GameClicked(dataId),
)

private fun scanSummaryRow(label: String, value: String) = LabelValueListModel(
    label = label,
    value = value,
    clickAction = SageAction.Noop,
)

private fun homeState(): HomeState {
    val generator = FakeModelGenerator()
    val games = generator.randomGames()
    val artists = generator.randomArtists()
    val tracks = generator.randomTracks(artists, games)
    return HomeState(
        sections = persistentListOf(
            // showHeader = false mirrors NowPlayingHomeModule's override — the card carries
            // its own title, so the section header would just duplicate it.
            section(
                NOW_PLAYING_ID,
                NOW_PLAYING_PRIORITY,
                "Now playing",
                nowPlayingItem(games.first()),
                showHeader = false
            ),
            section(
                GAME_OF_THE_DAY_ID,
                GAME_OF_THE_DAY_PRIORITY,
                "Game of the day",
                persistentListOf(
                    gameCard(
                        games.first().id + GAME_ID_OFFSET,
                        games.first(),
                        maxWidthDp = GAME_OF_THE_DAY_MAX_WIDTH_DP,
                    ),
                ),
            ),
            section(
                RECENTLY_ADDED_ID,
                RECENTLY_ADDED_PRIORITY,
                "Recently added",
                gameScrollerCards(games, RECENTLY_ADDED_GAME_OFFSET),
            ),
            section(
                RECENTLY_PLAYED_ID,
                RECENTLY_PLAYED_PRIORITY,
                "Recently played",
                gameScrollerCards(games, RECENTLY_PLAYED_GAME_OFFSET),
            ),
            section(MOST_PLAYED_SONGS_ID, MOST_PLAYED_SONGS_PRIORITY, "Most played songs", songCards(tracks)),
            section(MOST_PLAYED_ARTISTS_ID, MOST_PLAYED_ARTISTS_PRIORITY, "Most played artists", artistCards(artists)),
            section(SOLO_ID, SOLO_PRIORITY, "Featured", soloItem(games.first())),
            section(RNG_ID, RNG_PRIORITY, "RNG Take the Wheel", rngCards()),
        ),
    )
}

// A horizontal scroller of game-cover tiles — what the recently-played-games / most-played-games
// modules render.
private fun gameScrollerCards(games: List<Game>, offset: Long): ImmutableList<ListModel> = games
    .take(PREVIEW_SCROLLER_COUNT)
    .map { game -> gameCard(game.id + offset, game) }
    .toImmutableList()

// Song cells reuse the game-cover tile (game art + the song's title) — what the most-played-songs
// module renders.
private fun songCards(tracks: List<Track>): ImmutableList<ListModel> = tracks
    .take(PREVIEW_SCROLLER_COUNT)
    .map { track ->
        GridImageListModel(
            dataId = track.id + SONG_ID_OFFSET,
            name = track.title,
            sourceInfo = track.game?.photoUrl,
            imagePlaceholder = Icon.MusicNote,
            clickAction = HomeAction.SongClicked(track.id),
            aspectRatio = GAME_COVER_ASPECT_RATIO,
        )
    }
    .toImmutableList()

// Artist cells are square (the default 1f aspect).
private fun artistCards(artists: List<Artist>): ImmutableList<ListModel> = artists
    .take(PREVIEW_SCROLLER_COUNT)
    .map { artist ->
        GridImageListModel(
            dataId = artist.id + ARTIST_ID_OFFSET,
            name = artist.name,
            sourceInfo = artist.photoUrl,
            imagePlaceholder = Icon.Person,
            clickAction = HomeAction.ArtistClicked(artist.id),
        )
    }
    .toImmutableList()

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
            id = GAME_OF_THE_DAY_ID,
            priority = GAME_OF_THE_DAY_PRIORITY,
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

private fun soloItem(game: Game): ImmutableList<ListModel> = persistentListOf(
    gameCard(SOLO_DATA_ID, game),
)

private fun gameCard(dataId: Long, game: Game, maxWidthDp: Float? = null) = GridImageListModel(
    dataId = dataId,
    name = game.title,
    sourceInfo = game.photoUrl,
    imagePlaceholder = Icon.Album,
    clickAction = HomeAction.GameClicked(game.id),
    aspectRatio = GAME_COVER_ASPECT_RATIO,
    maxWidthDp = maxWidthDp,
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
