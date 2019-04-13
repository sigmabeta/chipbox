package net.sigmabeta.chipbox.backend.vgm

import net.sigmabeta.chipbox.backend.Scanner

class ScannerImpl : Scanner {
    init {
        if (!BackendImpl.INITIALIZED) {
            System.loadLibrary(BackendImpl.NAME)
            BackendImpl.INITIALIZED = true
        }
    }

    override fun getBackendId() = BackendImpl.ID

    external override fun fileInfoSetup(path: String): String?

    external override fun fileInfoGetTrackCount(): Int

    external override fun fileInfoSetTrackNumber(trackNumber: Int)

    external override fun fileInfoTeardown()

    external override fun getFileTrackLength(): Long

    external override fun getFileIntroLength(): Long

    external override fun getFileLoopLength(): Long

    external override fun getFileTitle(): ByteArray?

    external override fun getFileGameTitle(): ByteArray?

    external override fun getFilePlatform(): ByteArray?

    external override fun getFileArtist(): ByteArray?
}
