package net.sigmabeta.chipbox.features.folderpicker

import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.SingleTextListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * One subfolder displayed in the picker. [path] is the platform-native identifier (absolute path
 * on JVM/Android) that round-trips back to [FolderPickerAction.FolderClicked] when the user
 * descends into this folder.
 */
data class FolderPickerEntry(
    val name: String,
    val path: String,
    val childFolderCount: Int,
    val childFileCount: Int,
)

/**
 * Pure renderer for the in-app folder picker. [currentPath] is null only briefly at construction
 * before the view model resolves the per-OS default; [entries] holds the subfolders shown in the
 * current directory, and [fileCount] is the count rolled up into a single aggregate row (files
 * aren't displayed individually per spec).
 *
 * The two CTAs are always pinned to the top: "Add this folder" commits the [currentPath] to the
 * library and starts a scan; "Cancel and exit" pops the screen.
 */
data class FolderPickerState(
    val currentPath: String? = null,
    val entries: List<FolderPickerEntry> = emptyList(),
    val fileCount: Int = 0,
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.FOLDER_PICKER_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = buildList {
        add(addCta(stringProvider))
        add(cancelCta(stringProvider))
        entries.forEach { add(folderRow(stringProvider, it)) }
        if (fileCount > 0) add(aggregateFilesRow(stringProvider))
    }

    private fun addCta(stringProvider: StringProvider) = CtaListModel(
        icon = Icon.Plus,
        name = stringProvider.getString(ChipboxStringId.FOLDER_PICKER_CTA_ADD),
        clickAction = FolderPickerAction.AddThisFolderClicked,
    )

    private fun cancelCta(stringProvider: StringProvider) = CtaListModel(
        icon = Icon.Clear,
        name = stringProvider.getString(ChipboxStringId.FOLDER_PICKER_CTA_CANCEL),
        clickAction = FolderPickerAction.CancelClicked,
    )

    private fun folderRow(
        stringProvider: StringProvider,
        entry: FolderPickerEntry,
    ) = LabelValueListModel(
        dataId = entry.path.hashCode().toLong(),
        label = entry.name,
        value = folderValueText(stringProvider, entry.childFolderCount, entry.childFileCount),
        clickAction = FolderPickerAction.FolderClicked(entry.path),
    )

    private fun aggregateFilesRow(stringProvider: StringProvider) = SingleTextListModel(
        name = stringProvider.getStringOneInt(ChipboxStringId.FOLDER_PICKER_VALUE_FILES, fileCount),
        clickAction = SageAction.Noop,
    )

    private fun folderValueText(
        stringProvider: StringProvider,
        folderCount: Int,
        fileCount: Int,
    ): String {
        if (folderCount == 0 && fileCount == 0) {
            return stringProvider.getString(ChipboxStringId.FOLDER_PICKER_VALUE_EMPTY)
        }
        val folders = if (folderCount > 0) {
            stringProvider.getStringOneInt(ChipboxStringId.FOLDER_PICKER_VALUE_FOLDERS, folderCount)
        } else {
            null
        }
        val files = if (fileCount > 0) {
            stringProvider.getStringOneInt(ChipboxStringId.FOLDER_PICKER_VALUE_FILES, fileCount)
        } else {
            null
        }
        return when {
            folders != null && files != null -> stringProvider.getStringTwoArgs(
                ChipboxStringId.FOLDER_PICKER_VALUE_FOLDERS_AND_FILES,
                folders,
                files,
            )

            folders != null -> folders

            else -> files!!
        }
    }
}
