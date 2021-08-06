package net.sigmabeta.chipbox.activities

import androidx.lifecycle.ViewModel
import net.sigmabeta.chipbox.scanner.Scanner
import javax.inject.Inject

class TopViewModel @Inject constructor(
    private val scanner: Scanner
):  ViewModel() {
    val scannerStates = scanner.state()
    val scannerEvents = scanner.scanEvents()

    fun startScan() {
        scanner.startScan()
    }
}