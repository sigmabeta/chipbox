package net.sigmabeta.chipbox.scanner

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.models.state.ScannerEvent
import net.sigmabeta.chipbox.models.state.ScannerState

interface Scanner {
    fun startScan()
    fun state(): Flow<ScannerState>
    fun scanEvents(): Flow<ScannerEvent>
}
