package net.sigmabeta.chipbox.features.managelibrary.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.managelibrary.LibraryFolder
import net.sigmabeta.chipbox.features.managelibrary.ManageLibraryState
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun ManageLibrary(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = manageLibraryState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun ManageLibraryEmpty(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = ManageLibraryState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun manageLibraryState() = ManageLibraryState(
    folders = listOf(
        LibraryFolder("/storage/emulated/0/Music/NSF", "NSF"),
        LibraryFolder("/storage/emulated/0/Music/SPC", "SPC"),
        LibraryFolder("/storage/emulated/0/Music/PSF", "PSF"),
    ),
)
