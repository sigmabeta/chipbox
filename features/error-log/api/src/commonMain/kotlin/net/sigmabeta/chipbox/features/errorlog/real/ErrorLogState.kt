package net.sigmabeta.chipbox.features.errorlog.real

import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.NameCaptionListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.logging.HatchetError
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class ErrorLogState(
    val errors: List<HatchetError> = emptyList(),
) : ListState() {
    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.ERROR_LOG_SCREEN_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> {
        if (errors.isEmpty()) {
            return listOf(
                EmptyStateListModel(
                    icon = Icon.Warning,
                    explanation = stringProvider.getString(ChipboxStringId.ERROR_LOG_EMPTY),
                    showCrossOut = false,
                )
            )
        }
        // dataId is the list index: Hatchet's recent-errors buffer is a fixed snapshot here, so the
        // position is stable and unique (two identical errors would collide on hashCode).
        return errors.mapIndexed { index, error -> errorRow(index, error) }
    }

    private fun errorRow(index: Int, error: HatchetError) = NameCaptionListModel(
        dataId = index.toLong(),
        name = error.message,
        caption = caption(error),
        clickAction = SageAction.Noop,
    )

    // "12:34:56 · SomeTag · main". tag/thread are blank for the commonMain Hatchets
    // (BasicHatchet/BluntHatchet, which don't walk the stack), so drop the empty pieces.
    private fun caption(error: HatchetError): String =
        listOf(formatErrorTimestamp(error.timestamp), error.tag, error.thread)
            .filter { it.isNotBlank() }
            .joinToString(SEPARATOR)

    private companion object {
        private const val SEPARATOR = " · "
    }
}
