package net.sigmabeta.chipbox.features.folderpicker.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.folderpicker.FolderPickerEntry
import net.sigmabeta.chipbox.features.folderpicker.FolderPickerState
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun FolderPicker(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = populatedState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun FolderPickerEmpty(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = FolderPickerState(currentPath = "/home/sigma/Music"),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun populatedState() = FolderPickerState(
    currentPath = "/home/sigma/Music",
    entries = listOf(
        FolderPickerEntry("NSF", "/home/sigma/Music/NSF", childFolderCount = 4, childFileCount = 0),
        FolderPickerEntry("PSF", "/home/sigma/Music/PSF", childFolderCount = 0, childFileCount = 12),
        FolderPickerEntry("SPC", "/home/sigma/Music/SPC", childFolderCount = 3, childFileCount = 27),
        FolderPickerEntry("empty-folder", "/home/sigma/Music/empty", childFolderCount = 0, childFileCount = 0),
    ),
    fileCount = 5,
)
