package net.sigmabeta.chipbox.features.browsebyplatform.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatformState
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun BrowseByPlatform(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = browseByPlatformState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun BrowseByPlatformLoading(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = browseByPlatformLoadingState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun browseByPlatformState(): BrowseByPlatformState {
    val platforms = Platform.entries.filter { it != Platform.OTHER }
    return BrowseByPlatformState(platforms = LCE.Content(platforms))
}

private fun browseByPlatformLoadingState(): BrowseByPlatformState {
    val name = FakeModelGenerator().loadingName()
    return BrowseByPlatformState(platforms = LCE.Loading(name))
}
