package net.sigmabeta.chipbox.common.appui.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.ThemeMode
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.sage.di.AppScope

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class ChipboxAppUiViewModel @Inject constructor(
    settingsManager: ChipboxSettingsManager,
) : ViewModel() {
    /** The user's persisted theme choice; drives `ChipboxTheme`'s light/dark scheme. */
    val themeMode: StateFlow<ThemeMode> = settingsManager.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DEFAULT)

    /** The user's persisted brand/plain typefaces, applied by `ChipboxTheme`. */
    val brandFont: StateFlow<ChipboxFont> = settingsManager.getBrandFont()
        .map { ChipboxFont.fromStorageValue(it, ChipboxFont.DEFAULT_BRAND) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChipboxFont.DEFAULT_BRAND)

    val plainFont: StateFlow<ChipboxFont> = settingsManager.getPlainFont()
        .map { ChipboxFont.fromStorageValue(it, ChipboxFont.DEFAULT_PLAIN) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChipboxFont.DEFAULT_PLAIN)
}
