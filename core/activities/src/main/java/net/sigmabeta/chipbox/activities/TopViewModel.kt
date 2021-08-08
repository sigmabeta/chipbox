package net.sigmabeta.chipbox.activities

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import net.sigmabeta.chipbox.scanner.Scanner
import javax.inject.Inject

class TopViewModel @Inject constructor(
    private val scanner: Scanner
):  ViewModel() {
    @OptIn(FlowPreview::class)
    val scannerEvents = scanner.scanEvents().sample(700L)
    val scannerStates = scanner.state()

    fun startScan() {
        scanner.startScan()
    }

    fun clearScan() {
        scanner.clearScan()
    }
}