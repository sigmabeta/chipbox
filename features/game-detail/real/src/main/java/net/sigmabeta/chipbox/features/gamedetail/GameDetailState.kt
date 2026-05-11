package net.sigmabeta.chipbox.features.gamedetail

import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.HeroImageListModel
import net.sigmabeta.sage.components.HorizontalScrollerListModel
import net.sigmabeta.sage.components.ImageNameListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.components.WideItemListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class GameDetailState(
    val game: LCE<Game> = LCE.Uninitialized,
    val tracks: LCE<List<Track>> = LCE.Uninitialized,
    val artists: LCE<List<Artist>> = LCE.Uninitialized,
) : ListState() {
    override val columnType: ColumnType = ColumnType.Staggered(STAGGERED_WIDTH_DP, false)

    override fun title(stringProvider: StringProvider) = when (game) {
        is LCE.Content -> TitleBarModel(title = game.data.title)
        else -> TitleBarModel()
    }

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = listOf(
        heroSection(),
        ctaSection(stringProvider),
        songSection(stringProvider),
        artistSection(stringProvider),
    )

    private fun heroSection() =
        game.sectionWithStandardErrorAndLoading(
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
                        imagePlaceholder = Icon.ALBUM,
                        contentDescription = data.title,
                        clickAction = GameDetailAction.PlayAllClicked,
                    ),
                )
            } else {
                emptyList()
            }
        }

    private fun ctaSection(stringProvider: StringProvider) =
        game.sectionWithStandardErrorAndLoading(
            sectionName = SECTION_NAME_CTA,
            loadingItemCount = 0,
            loadingWithHeader = false,
        ) {
            listOf(
                CtaListModel(
                    icon = Icon.MUSIC_NOTE,
                    name = stringProvider.getString(ChipboxStringId.GAME_DETAIL_CTA_PLAY_ALL),
                    clickAction = GameDetailAction.PlayAllClicked,
                ),
                CtaListModel(
                    icon = Icon.SHUFFLE,
                    name = stringProvider.getString(ChipboxStringId.GAME_DETAIL_CTA_SHUFFLE_ALL),
                    clickAction = GameDetailAction.ShuffleAllClicked,
                ),
            )
        }

    private fun songSection(stringProvider: StringProvider) =
        tracks.sectionWithStandardErrorAndLoading(
            sectionName = SECTION_NAME_SONGS,
            loadingType = LoadingType.TEXT_IMAGE,
            loadingItemCount = SONGS_LOADING_COUNT,
            loadingWithHeader = true,
        ) {
            listOf(
                SectionHeaderListModel(
                    stringProvider.getString(ChipboxStringId.GAME_DETAIL_SECTION_SONGS),
                ),
            ) + data.mapIndexed { index, track ->
                ImageNameListModel(
                    dataId = track.id + ID_PREFIX_SONGS,
                    name = track.title,
                    sourceInfo = SourceInfo(null),
                    imagePlaceholder = Icon.MUSIC_NOTE,
                    clickAction = GameDetailAction.TrackClicked(index),
                )
            }
        }

    private fun artistSection(stringProvider: StringProvider) =
        artists.sectionWithStandardErrorAndLoading(
            sectionName = SECTION_NAME_ARTISTS,
            loadingType = LoadingType.WIDE_ITEM,
            loadingWithHeader = true,
            loadingHorizScrollable = true,
        ) {
            listOf(
                SectionHeaderListModel(
                    stringProvider.getString(ChipboxStringId.GAME_DETAIL_SECTION_ARTISTS),
                ),
                HorizontalScrollerListModel(
                    dataId = SECTION_NAME_ARTISTS.hashCode().toLong() + ID_PREFIX_SCROLLER,
                    scrollingItems = data.map { artist ->
                        WideItemListModel(
                            dataId = artist.id + ID_PREFIX_ARTISTS,
                            name = artist.name,
                            sourceInfo = artist.photoUrl,
                            imagePlaceholder = Icon.PERSON,
                            clickAction = GameDetailAction.ArtistClicked(artist.id),
                        )
                    }.toImmutableList(),
                ),
            )
        }

    companion object {
        private const val SECTION_NAME_HERO = "section.hero"
        private const val SECTION_NAME_CTA = "section.cta"
        private const val SECTION_NAME_SONGS = "section.songs"
        private const val SECTION_NAME_ARTISTS = "section.artists"

        private const val STAGGERED_WIDTH_DP = 320
        private const val SONGS_LOADING_COUNT = 8

        private const val ID_PREFIX_SONGS = 1_000_000L
        private const val ID_PREFIX_ARTISTS = 1_000_000_000L
        private const val ID_PREFIX_SCROLLER = 1_000_000_000_000L
    }
}
