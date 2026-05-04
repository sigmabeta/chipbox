package net.sigmabeta.chipbox.activities

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.sample
import net.sigmabeta.chipbox.contentsource.AndroidFileContentSource
import net.sigmabeta.chipbox.scanner.Scanner
import javax.inject.Inject

class TopViewModel @Inject constructor(
    private val scanner: Scanner,
    private val androidFileContentSource: AndroidFileContentSource,
):  ViewModel() {
    @OptIn(FlowPreview::class)
    val scannerEvents = scanner.scanEvents().sample(700L)
    val scannerStates = scanner.state()

    private val permission = MutableStateFlow(false)
    val permissionGranted = permission.asStateFlow()

    fun directoryPermissionGranted(uri: Uri) {
        androidFileContentSource.addLibraryLocation(uri)
    }

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