package net.sigmabeta.chipbox.features.gamesforplatform.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatformState
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun GamesForPlatform(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = gamesForPlatformState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun GamesForPlatformLoading(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = gamesForPlatformLoadingState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun gamesForPlatformState(): GamesForPlatformState {
    val games = FakeModelGenerator().randomGames()
    return GamesForPlatformState(
        platform = Platform.SNES,
        games = LCE.Content(games),
    )
}

private fun gamesForPlatformLoadingState(): GamesForPlatformState {
    val name = FakeModelGenerator().loadingName()
    return GamesForPlatformState(
        platform = Platform.SNES,
        games = LCE.Loading(name),
    )
}
