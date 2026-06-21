package net.sigmabeta.chipbox.features.artistdetail

import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.HeroImageListModel
import net.sigmabeta.sage.components.HorizontalScrollerListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.components.WideItemListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class ArtistDetailState(
    val artist: LCE<Artist> = LCE.Uninitialized,
    val tracks: LCE<List<Track>> = LCE.Uninitialized,
    val games: LCE<List<Game>> = LCE.Uninitialized,
    val playingTrackId: Long? = null,
    val isFavorite: Boolean = false,
    val notFound: Boolean = false,
) : ListState() {
    override val columnType: ColumnType = ColumnType.Staggered(STAGGERED_WIDTH_DP, false)

    override fun title(stringProvider: StringProvider) = when (artist) {
        is LCE.Content -> TitleBarModel(title = artist.data.name)
        else -> TitleBarModel()
    }

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = if (notFound) {
        listOf(
            EmptyStateListModel(
                icon = Icon.Person,
                explanation = stringProvider.getString(ChipboxStringId.ARTIST_DETAIL_EMPTY),
            )
        )
    } else {
        listOf(
            heroSection(),
            ctaSection(stringProvider),
            gamesSection(stringProvider),
            songSection(stringProvider),
        )
    }

    private fun heroSection() = artist.sectionWithStandardErrorAndLoading(
            sectionName = SECTION_NAME_HERO,
            loadingType = LoadingType.BIG_IMAGE,
            loadingItemCount = 1,
            loadingWithHeader = false,
        ) {
            val photoUrl = data.photoUrl
            if (photoUrl != null) {
                listOf(
                    HeroImageListModel(
                        sourceInfo = SourceInfo(photoUrl),
                        imagePlaceholder = Icon.Person,
                        contentDescription = data.name,
                        clickAction = ArtistDetailAction.PlayAllClicked,
                    ),
                )
            } else {
                emptyList()
            }
        }

    private fun ctaSection(stringProvider: StringProvider) = artist.sectionWithStandardErrorAndLoading(
            sectionName = SECTION_NAME_CTA,
            loadingItemCount = 0,
            loadingWithHeader = false,
        ) {
            listOf(
                CtaListModel(
                    icon = Icon.MusicNote,
                    name = stringProvider.getString(ChipboxStringId.ARTIST_DETAIL_CTA_PLAY_ALL),
                    clickAction = ArtistDetailAction.PlayAllClicked,
                ),
                CtaListModel(
                    icon = Icon.Shuffle,
                    name = stringProvider.getString(ChipboxStringId.ARTIST_DETAIL_CTA_SHUFFLE_ALL),
                    clickAction = ArtistDetailAction.ShuffleAllClicked,
                ),
                CtaListModel(
                    icon = if (isFavorite) Icon.FavoriteFilled else Icon.FavoriteEmpty,
                    name = stringProvider.getString(
                        if (isFavorite) {
                            ChipboxStringId.ARTIST_DETAIL_CTA_REMOVE_FROM_FAVORITES
                        } else {
                            ChipboxStringId.ARTIST_DETAIL_CTA_ADD_TO_FAVORITES
                        },
                    ),
                    clickAction = ArtistDetailAction.AddToFavoritesClicked,
                ),
            )
        }

    private fun songSection(stringProvider: StringProvider) = tracks.sectionWithStandardErrorAndLoading(
            sectionName = SECTION_NAME_SONGS,
            loadingType = LoadingType.TEXT_CAPTION,
            loadingItemCount = SONGS_LOADING_COUNT,
            loadingWithHeader = true,
        ) {
            listOf(
                SectionHeaderListModel(
                    stringProvider.getString(ChipboxStringId.ARTIST_DETAIL_SECTION_SONGS),
                ),
            ) + data.mapIndexed(::trackRow)
        }

    private fun trackRow(index: Int, track: Track): ListModel = NameCaptionValueListModel(
            dataId = track.id + ID_PREFIX_SONGS,
            name = track.title,
            caption = track.game?.title.orEmpty(),
            value = formatTrackLength(track.trackLengthMs),
            clickAction = ArtistDetailAction.TrackClicked(index),
            active = track.id == playingTrackId,
        )

    private fun gamesSection(stringProvider: StringProvider) = games.sectionWithStandardErrorAndLoading(
            sectionName = SECTION_NAME_GAMES,
            loadingType = LoadingType.WIDE_ITEM,
            loadingWithHeader = true,
            loadingHorizScrollable = true,
        ) {
            listOf(
                SectionHeaderListModel(
                    stringProvider.getString(ChipboxStringId.ARTIST_DETAIL_SECTION_GAMES),
                ),
                HorizontalScrollerListModel(
                    dataId = SECTION_NAME_GAMES.hashCode().toLong() + ID_PREFIX_SCROLLER,
                    scrollingItems = data.map { game ->
                        WideItemListModel(
                            dataId = game.id + ID_PREFIX_GAMES,
                            name = game.title,
                            sourceInfo = game.photoUrl,
                            imagePlaceholder = Icon.Album,
                            clickAction = ArtistDetailAction.GameClicked(game.id),
                        )
                    }.toImmutableList(),
                ),
            )
        }

    private fun formatTrackLength(millis: Long): String {
        val totalSeconds = millis / MILLIS_PER_SECOND
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    companion object {
        private const val SECTION_NAME_HERO = "section.hero"
        private const val SECTION_NAME_CTA = "section.cta"
        private const val SECTION_NAME_SONGS = "section.songs"
        private const val SECTION_NAME_GAMES = "section.games"

        private const val STAGGERED_WIDTH_DP = 320
        private const val SONGS_LOADING_COUNT = 8

        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60L

        private const val ID_PREFIX_SONGS = 1_000_000L
        private const val ID_PREFIX_GAMES = 1_000_000_000L
        private const val ID_PREFIX_SCROLLER = 1_000_000_000_000L
    }
}
