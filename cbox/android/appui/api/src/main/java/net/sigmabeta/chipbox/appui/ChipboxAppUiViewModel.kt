package net.sigmabeta.chipbox.appui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint

@HiltViewModel
class ChipboxAppUiViewModel @Inject constructor(
    val playbackStatusEntryPoint: PlaybackStatusEntryPoint,
) : ViewModel()
