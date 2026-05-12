package net.sigmabeta.chipbox.features.playbackstatus.real

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.StringProvider

data class PlaybackStatusState(
    val track: Track? = null,
    val playback: ChipboxPlaybackState? = null,
    val session: Session? = null,
) : ListState() {

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.PLAYBACK_STATUS_SCREEN_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = buildList {
        addAll(playbackSection(stringProvider))
        addAll(trackSection(stringProvider))
        addAll(sessionSection(stringProvider))
    }

    private fun playbackSection(stringProvider: StringProvider): List<ListModel> = listOf(
        section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_PLAYBACK),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_STATE, playback?.state?.name),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_POSITION_MS,
            playback?.position?.toString()),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_LENGTH_MS,
            track?.trackLengthMs?.toString()),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_BUFFER_AHEAD_MS,
            playback?.let { (it.generatorProducedMs - it.position).toString() }),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_PLAYBACK_SPEED,
            playback?.playbackSpeed?.toString()),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_SKIP_FORWARD,
            playback?.skipForwardAllowed?.toString()),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_ERROR_MESSAGE,
            playback?.errorMessage ?: "—"),
    )

    private fun trackSection(stringProvider: StringProvider): List<ListModel> = listOf(
        section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_TRACK),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_TITLE, track?.title),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_ARTISTS,
            track?.artists?.joinToString { it.name }),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_GAME, track?.game?.title),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_TRACK_NUMBER,
            track?.trackNumber?.toString()),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_SOURCE, track?.source),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_FADE, track?.fade?.toString()),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_PATH, track?.path),
    )

    private fun sessionSection(stringProvider: StringProvider): List<ListModel> = listOf(
        section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_SESSION),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_SESSION_ID,
            session?.id?.toString()),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_SESSION_TYPE,
            session?.type?.name),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_CONTENT_ID,
            session?.contentId?.toString()),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_CURRENT_POSITION,
            session?.currentPosition?.toString()),
        row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_SHUFFLED,
            session?.shuffled?.toString()),
    )

    private fun section(stringProvider: StringProvider, id: ChipboxStringId) =
        SectionHeaderListModel(title = stringProvider.getString(id))

    private fun row(stringProvider: StringProvider, id: ChipboxStringId, value: String?) =
        LabelValueListModel(
            label = stringProvider.getString(id),
            value = value,
            clickAction = SageAction.Noop,
            dataId = id.hashCode().toLong(),
        )
}
