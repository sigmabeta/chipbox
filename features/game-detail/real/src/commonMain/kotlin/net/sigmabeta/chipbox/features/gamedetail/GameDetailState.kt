package net.sigmabeta.chipbox.features.gamedetail

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
import net.sigmabeta.sage.components.LabelValueListModel
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

data class GameDetailState(
    val game: LCE<Game> = LCE.Uninitialized,
    val tracks: LCE<List<Track>> = LCE.Uninitialized,
    val artists: LCE<List<Artist>> = LCE.Uninitialized,
    val playingTrackId: Long? = null,
    val isFavorite: Boolean = false,
    val notFound: Boolean = false,
) : ListState() {
    override val columnType: ColumnType = ColumnType.Staggered(STAGGERED_WIDTH_DP, false)

    override fun title(stringProvider: StringProvider) = when (game) {
        is LCE.Content -> TitleBarModel(title = game.data.title)
        else -> TitleBarModel()
    }

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = if (notFound) {
        listOf(
            EmptyStateListModel(
                icon = Icon.Album,
                explanation = stringProvider.getString(ChipboxStringId.GAME_DETAIL_EMPTY),
            )
        )
    } else {
        listOf(
            heroSection(),
            ctaSection(stringProvider),
            artistSection(stringProvider),
            songSection(stringProvider),
        )
    }

    private fun heroSection() = game.sectionWithStandardErrorAndLoading(
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
                        imagePlaceholder = Icon.Album,
                        contentDescription = data.title,
                        clickAction = GameDetailAction.PlayAllClicked,
                    ),
                )
            } else {
                emptyList()
            }
        }

    private fun ctaSection(stringProvider: StringProvider) = game.sectionWithStandardErrorAndLoading(
            sectionName = SECTION_NAME_CTA,
            loadingItemCount = 0,
            loadingWithHeader = false,
        ) {
            listOf(
                CtaListModel(
                    icon = Icon.MusicNote,
                    name = stringProvider.getString(ChipboxStringId.GAME_DETAIL_CTA_PLAY_ALL),
                    clickAction = GameDetailAction.PlayAllClicked,
                ),
                CtaListModel(
                    icon = Icon.Shuffle,
                    name = stringProvider.getString(ChipboxStringId.GAME_DETAIL_CTA_SHUFFLE_ALL),
                    clickAction = GameDetailAction.ShuffleAllClicked,
                ),
                CtaListModel(
                    icon = if (isFavorite) Icon.FavoriteFilled else Icon.FavoriteEmpty,
                    name = stringProvider.getString(
                        if (isFavorite) {
                            ChipboxStringId.GAME_DETAIL_CTA_REMOVE_FROM_FAVORITES
                        } else {
                            ChipboxStringId.GAME_DETAIL_CTA_ADD_TO_FAVORITES
                        },
                    ),
                    clickAction = GameDetailAction.AddToFavoritesClicked,
                ),
            )
        }

    private fun songSection(stringProvider: StringProvider) = tracks.sectionWithStandardErrorAndLoading(
            sectionName = SECTION_NAME_SONGS,
            loadingType = LoadingType.TEXT_CAPTION,
            loadingItemCount = SONGS_LOADING_COUNT,
            loadingWithHeader = true,
        ) {
            // When the game has a single artist, every track's caption would
            // repeat the same name — drop it and fall back to the simpler
            // label/value row.
            val gameArtistCount = (artists as? LCE.Content)?.data?.size ?: 0
            val captionPerTrack = gameArtistCount > 1

            listOf(
                SectionHeaderListModel(
                    stringProvider.getString(ChipboxStringId.GAME_DETAIL_SECTION_SONGS),
                ),
            ) + data.mapIndexed { index, track ->
                trackRow(index, track, captionPerTrack)
            }
        }

    private fun trackRow(
        index: Int,
        track: Track,
        captionPerTrack: Boolean,
    ): ListModel {
        val dataId = track.id + ID_PREFIX_SONGS
        val length = formatTrackLength(track.trackLengthMs)
        val clickAction = GameDetailAction.TrackClicked(index)

        val active = track.id == playingTrackId

        return if (captionPerTrack) {
            NameCaptionValueListModel(
                dataId = dataId,
                name = track.title,
                caption = track.artists.orEmpty().joinToString { it.name },
                value = length,
                clickAction = clickAction,
                active = active,
            )
        } else {
            LabelValueListModel(
                dataId = dataId,
                label = track.title,
                value = length,
                clickAction = clickAction,
                active = active,
            )
        }
    }

    private fun artistSection(stringProvider: StringProvider) = artists.sectionWithStandardErrorAndLoading(
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
                            imagePlaceholder = Icon.Person,
                            clickAction = GameDetailAction.ArtistClicked(artist.id),
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
        private const val SECTION_NAME_ARTISTS = "section.artists"

        private const val STAGGERED_WIDTH_DP = 320
        private const val SONGS_LOADING_COUNT = 8

        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60L

        private const val ID_PREFIX_SONGS = 1_000_000L
        private const val ID_PREFIX_ARTISTS = 1_000_000_000L
        private const val ID_PREFIX_SCROLLER = 1_000_000_000_000L
    }
}
