package net.sigmabeta.chipbox.features.managelibrary

import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.NameCaptionListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * One library folder the user can remove. [identifier] is the platform string a [LibrarySource]
 * round-trips (SAF tree-doc URI on Android, absolute path on JVM); [displayName] is the friendly
 * folder name when the source knows it.
 */
data class LibraryFolder(
    val identifier: String,
    val displayName: String?,
)

data class ManageLibraryState(
    val folders: List<LibraryFolder> = emptyList(),
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.MANAGE_LIBRARY_TITLE),
        shouldShowBack = true,
    )

    // CTA pinned to the top; folder rows (or the empty state) follow. Tapping a row removes that
    // folder — the spec's "clicking a folder removes it".
    override fun toListItems(stringProvider: StringProvider): List<ListModel> =
        listOf(addFolderCta(stringProvider)) + folderRows(stringProvider)

    private fun addFolderCta(stringProvider: StringProvider) = CtaListModel(
        icon = Icon.Plus,
        name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_ADD_FOLDER),
        clickAction = ManageLibraryAction.AddFolderClicked,
    )

    private fun folderRows(stringProvider: StringProvider): List<ListModel> {
        if (folders.isEmpty()) {
            return listOf(
                EmptyStateListModel(
                    icon = Icon.MusicNote,
                    explanation = stringProvider.getString(ChipboxStringId.MANAGE_LIBRARY_EMPTY),
                    showCrossOut = false,
                ),
            )
        }
        return folders.map { folder ->
            NameCaptionListModel(
                dataId = folder.identifier.hashCode().toLong(),
                name = folder.displayName ?: folder.identifier,
                caption = folder.identifier,
                clickAction = ManageLibraryAction.FolderClicked(folder.identifier),
            )
        }
    }
}
