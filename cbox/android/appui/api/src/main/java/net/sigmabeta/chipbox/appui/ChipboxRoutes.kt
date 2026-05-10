package net.sigmabeta.chipbox.appui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import net.sigmabeta.chipbox.strings.ChipboxStringId

@Serializable
data object Library

@Serializable
data object Search

@Serializable
data object Settings

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    val labelId: ChipboxStringId,
) {
    LIBRARY(Library, Library::class, Icons.Filled.LibraryMusic, ChipboxStringId.APPUI_TAB_LIBRARY),
    SEARCH(Search, Search::class, Icons.Filled.Search, ChipboxStringId.APPUI_TAB_SEARCH),
    SETTINGS(Settings, Settings::class, Icons.Filled.Settings, ChipboxStringId.APPUI_TAB_SETTINGS),
}
