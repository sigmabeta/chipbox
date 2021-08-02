package net.sigmabeta.chipbox.scanner

import kotlinx.coroutines.flow.Flow


interface Scanner {
    fun scanEvents(): Flow<ScannerEvent>
}
