package net.sigmabeta.chipbox.features.folderpicker

import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EmptyStateListModel
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
 * The CTAs are always pinned to the top: "Add this folder" commits the [currentPath] to the
 * library and starts a scan; "Go up a folder" ascends to [parentPath] (shown only when there is
 * one); the hidden-files toggle flips [showHidden]; "Cancel and exit" pops the screen.
 *
 * [readable] is false when the current directory couldn't be enumerated (permission denied) — the
 * common case for Android's traverse-only storage parents above `/storage/emulated/0`. In that
 * state the screen collapses to an explanatory error plus the "Go up"/"Cancel" escapes; "Add" and
 * the hidden-files toggle are dropped since there's nothing to add or reveal.
 */
data class FolderPickerState(
    val currentPath: String? = null,
    val entries: List<FolderPickerEntry> = emptyList(),
    val fileCount: Int = 0,
    val parentPath: String? = null,
    val showHidden: Boolean = false,
    val readable: Boolean = true,
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        // Show where the user is, not a static label. [currentPath] is null only for the first
        // frame before the view model resolves the default directory — fall back to the label then.
        title = currentPath?.middleEllipsize(MAX_TITLE_LENGTH)
            ?: stringProvider.getString(ChipboxStringId.FOLDER_PICKER_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = buildList {
        if (!readable) {
            // Nothing to enumerate: offer only the escapes and explain why. "Go up" is still
            // offered when there's a parent, for the case of an isolated unreadable folder under a
            // readable one. "Return to default folder" sits right below the error message so it
            // reads as the obvious fix — it's the reliable escape, since ascending from here
            // (Android's storage-root chain) just lands on more traverse-only, unreadable parents.
            if (parentPath != null) add(upCta(stringProvider))
            add(cancelCta(stringProvider))
            add(unreadableError(stringProvider))
            add(returnToDefaultCta(stringProvider))
            return@buildList
        }
        add(addCta(stringProvider))
        if (parentPath != null) add(upCta(stringProvider))
        add(toggleHiddenCta(stringProvider))
        add(cancelCta(stringProvider))
        entries.forEach { add(folderRow(stringProvider, it)) }
        if (fileCount > 0) add(aggregateFilesRow(stringProvider))
    }

    private fun addCta(stringProvider: StringProvider) = CtaListModel(
        icon = Icon.Plus,
        name = stringProvider.getString(ChipboxStringId.FOLDER_PICKER_CTA_ADD),
        clickAction = FolderPickerAction.AddThisFolderClicked,
    )

    private fun upCta(stringProvider: StringProvider) = CtaListModel(
        icon = Icon.Back,
        name = stringProvider.getString(ChipboxStringId.FOLDER_PICKER_CTA_UP),
        clickAction = FolderPickerAction.NavigateUpClicked,
    )

    private fun toggleHiddenCta(stringProvider: StringProvider) = CtaListModel(
        icon = if (showHidden) Icon.VisibilityOff else Icon.Visibility,
        name = stringProvider.getString(
            if (showHidden) {
                ChipboxStringId.FOLDER_PICKER_CTA_HIDE_HIDDEN
            } else {
                ChipboxStringId.FOLDER_PICKER_CTA_SHOW_HIDDEN
            },
        ),
        clickAction = FolderPickerAction.ToggleHiddenClicked,
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

    private fun returnToDefaultCta(stringProvider: StringProvider) = CtaListModel(
        icon = Icon.Home,
        name = stringProvider.getString(ChipboxStringId.FOLDER_PICKER_CTA_RETURN_TO_DEFAULT),
        clickAction = FolderPickerAction.ReturnToDefaultClicked,
    )

    private fun unreadableError(stringProvider: StringProvider) = EmptyStateListModel(
        icon = Icon.Warning,
        explanation = stringProvider.getString(ChipboxStringId.FOLDER_PICKER_ERROR_UNREADABLE),
        // Not a "crossed-out empty" — it's a permission wall, so show the warning glyph plainly.
        showCrossOut = false,
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

    /**
     * Collapse the middle of an over-long path to a single ellipsis so the head (filesystem root)
     * and tail (the folder you're actually in) both stay visible — far more useful than an
     * end-truncated path that hides the current folder name. Total length is capped at [max].
     */
    private fun String.middleEllipsize(max: Int): String {
        if (length <= max) return this
        val keep = max - ELLIPSIS.length
        val head = (keep + 1) / 2
        val tail = keep - head
        return take(head) + ELLIPSIS + takeLast(tail)
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 64
        const val ELLIPSIS = "…"
    }
}
