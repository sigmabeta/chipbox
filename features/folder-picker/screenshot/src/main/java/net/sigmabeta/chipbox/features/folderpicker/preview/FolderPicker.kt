package net.sigmabeta.chipbox.features.folderpicker.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.folderpicker.FolderPickerEntry
import net.sigmabeta.chipbox.features.folderpicker.FolderPickerState
import net.sigmabeta.chipbox.features.folderpicker.StorageVolumeInfo
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

@DevicePreviews
@Composable
internal fun FolderPickerUnreadable(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        // A traverse-only Android storage parent: no entries, only the escapes + the error state.
        screenState = FolderPickerState(
            currentPath = "/storage/emulated",
            parentPath = "/storage",
            readable = false,
        ),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun FolderPickerVolumes(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        // The synthetic storage-volume chooser reached by going "up" from a volume root.
        screenState = FolderPickerState(
            atVolumeList = true,
            volumes = listOf(
                StorageVolumeInfo("Internal shared storage", "/storage/emulated/0"),
                StorageVolumeInfo("SD card", "/storage/1A2B-3C4D"),
            ),
        ),
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
    // Non-null parent so the golden exercises the "Go up a folder" CTA alongside the hidden-files toggle.
    parentPath = "/home/sigma",
)
