package net.sigmabeta.chipbox.features.playbackstatus.real

import net.sigmabeta.chipbox.debuginfo.PlaybackDebugInfo
import net.sigmabeta.chipbox.player.common.VolumeProcessor
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.utils.formatDecimal
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class PlaybackStatusState(
    val debug: PlaybackDebugInfo? = null,
) : ListState() {

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.PLAYBACK_STATUS_SCREEN_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = buildList {
        add(
            CtaListModel(
                icon = Icon.Description,
                name = stringProvider.getString(
                    ChipboxStringId.PLAYBACK_STATUS_CTA_COPY_DEBUG_INFO
                ),
                clickAction = PlaybackStatusAction.CopyDebugInfoClicked,
            )
        )
        addAll(playbackSection(stringProvider))
        addAll(trackSection(stringProvider))
        addAll(sessionSection(stringProvider))
        addAll(generatorSection(stringProvider))
        addAll(speakerSection(stringProvider))
        addAll(volumeSection(stringProvider))
        addAll(resamplerSection(stringProvider))
        addAll(bufferSection(stringProvider))
    }

    private fun playbackSection(stringProvider: StringProvider): List<ListModel> {
        val playback = debug?.playback
        val track = debug?.track
        return listOf(
            section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_PLAYBACK),
            row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_STATE, playback?.state?.name),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_POSITION_MS,
                playback?.position?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_LENGTH_MS,
                track?.trackLengthMs?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_BUFFER_AHEAD_MS,
                playback?.let { (it.generatorProducedMs - it.position).toString() }
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_PLAYBACK_SPEED,
                playback?.playbackSpeed?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_SKIP_FORWARD,
                playback?.skipForwardAllowed?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_ERROR_MESSAGE,
                playback?.errorMessage ?: "—"
            ),
        )
    }

    private fun trackSection(stringProvider: StringProvider): List<ListModel> {
        val track = debug?.track
        return listOf(
            section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_TRACK),
            row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_TITLE, track?.title),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_ARTISTS,
                track?.artists?.joinToString { it.name }
            ),
            row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_GAME, track?.game?.title),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_TRACK_NUMBER,
                track?.trackNumber?.toString()
            ),
            row(stringProvider, ChipboxStringId.PLAYBACK_STATUS_LABEL_SOURCE, track?.source),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_FADE,
                track?.fadeLengthMs?.let { "${it}ms" },
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_PATH,
                shortenPath(track?.path)
            ),
        )
    }

    private fun sessionSection(stringProvider: StringProvider): List<ListModel> {
        val session = debug?.session
        return listOf(
            section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_SESSION),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_SESSION_ID,
                session?.id?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_SESSION_TYPE,
                session?.type?.name
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_CONTENT_ID,
                session?.contentId?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_CURRENT_POSITION,
                session?.currentPosition?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_SHUFFLED,
                session?.shuffled?.toString()
            ),
        )
    }

    private fun generatorSection(stringProvider: StringProvider): List<ListModel> {
        val generator = debug?.generator
        return listOf(
            section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_GENERATOR),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_TRACK_ID,
                generator?.currentTrackId?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_TRACK_TITLE,
                generator?.currentTrackTitle
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_SAMPLE_RATE,
                generator?.sampleRate?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_PRODUCED_MS,
                generator?.producedMs?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_FRAMES_PLAYED,
                generator?.framesPlayed?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_LOOPING,
                generator?.looping?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_LAST_EVENT,
                generator?.lastEvent?.let { it::class.simpleName }
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_LAST_ERROR,
                generator?.lastError ?: "—"
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_SOURCE_DIAG,
                generator?.sourceDiagnostics ?: "—"
            ),
        )
    }

    private fun speakerSection(stringProvider: StringProvider): List<ListModel> {
        val speaker = debug?.speaker
        return listOf(
            section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_SPEAKER),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_TRACK_ID,
                speaker?.playingTrackId?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_POSITION_MS,
                speaker?.positionMs?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_CONSUME_LOOP,
                speaker?.consumeLoopRunning?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_LAST_EVENT,
                speaker?.lastEvent?.let { it::class.simpleName }
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_UNDERRUNS,
                speaker?.underrunCount?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_LAST_ERROR,
                speaker?.lastError ?: "—"
            ),
        )
    }

    private fun volumeSection(stringProvider: StringProvider): List<ListModel> {
        val volume = debug?.speaker?.volume
        fun gain(value: Double?) = value?.let { formatDecimal(it, 3) }
        fun modification(key: String) = volume?.modifications?.get(key)?.let { formatDecimal(it, 3) } ?: "—"
        return listOf(
            section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_VOLUME),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_VOL_TARGET_GAIN,
                gain(volume?.targetGain)
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_VOL_ACTUAL_GAIN,
                gain(volume?.actualGain)
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_VOL_MAX_GAIN,
                gain(volume?.maxGain)
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_VOL_DUCK,
                modification(VolumeProcessor.KEY_DUCK)
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_VOL_MASTER,
                modification(VolumeProcessor.KEY_MASTER)
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_VOL_NORMALIZATION,
                modification(VolumeProcessor.KEY_NORMALIZATION)
            ),
        )
    }

    private fun resamplerSection(stringProvider: StringProvider): List<ListModel> {
        val resampler = debug?.speaker?.resampler
        return listOf(
            section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_RESAMPLER),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_RES_MODE,
                resampler?.mode
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_RES_ACTIVE,
                resampler?.active?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_RES_INPUT_RATE,
                resampler?.inputRateHz?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_RES_OUTPUT_RATE,
                resampler?.outputRateHz?.toString()
            ),
        )
    }

    private fun bufferSection(stringProvider: StringProvider): List<ListModel> {
        val buffer = debug?.buffer
        return listOf(
            section(stringProvider, ChipboxStringId.PLAYBACK_STATUS_SECTION_BUFFER),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_BUF_SAMPLE_RATE,
                buffer?.sampleRate?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_BUF_CAPACITY,
                buffer?.capacity?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_BUF_FULL_QUEUED,
                buffer?.fullBuffersQueued?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_BUF_EMPTY_AVAIL,
                buffer?.emptyArraysAvailable?.toString()
            ),
            row(
                stringProvider,
                ChipboxStringId.PLAYBACK_STATUS_LABEL_BUF_DRAIN_COUNT,
                buffer?.drainCount?.toString()
            ),
        )
    }

    private fun section(stringProvider: StringProvider, id: ChipboxStringId) =
        SectionHeaderListModel(title = stringProvider.getString(id))

    private fun row(stringProvider: StringProvider, id: ChipboxStringId, value: String?) = LabelValueListModel(
            label = stringProvider.getString(id),
            value = value,
            clickAction = SageAction.Noop,
            dataId = id.hashCode().toLong(),
        )

    /**
     * Decode URI escapes and keep only the trailing folder + filename, dropping the SAF authority
     * and tree-path ceremony. `content://.../tree/primary%3AMusic/document/primary%3AMusic%2FNES%2FZelda.nsf`
     * → `NES/Zelda.nsf`. Returns null/originals untouched if decoding fails or there's nothing to trim.
     */
    private fun shortenPath(path: String?): String? {
        if (path.isNullOrEmpty()) return path
        val decoded = runCatching { urlDecodeUtf8(path) }.getOrDefault(path)
        return decoded
            .split('/')
            .filter { it.isNotEmpty() }
            .takeLast(2)
            .joinToString("/")
            .ifEmpty { decoded }
    }
}
