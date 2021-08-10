package net.sigmabeta.chipbox.activities

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.sample
import net.sigmabeta.chipbox.scanner.Scanner
import javax.inject.Inject

class TopViewModel @Inject constructor(
    private val scanner: Scanner
):  ViewModel() {
    @OptIn(FlowPreview::class)
    val scannerEvents = scanner.scanEvents().sample(700L)
    val scannerStates = scanner.state()

    private val permission = MutableStateFlow(false)
    val permissionGranted = permission.asStateFlow()

    fun startScan() {
        scanner.startScan()
    }

    fun clearScan() {
        scanner.clearScan()
    }

    fun storagePermissionGranted() {
        permission.tryEmit(true)
    }

    fun storagePermissionDenied() {
        permission.tryEmit(false)
    }

    fun showPermissionExplanation() {
        TODO("Not yet implemented")
    }
}