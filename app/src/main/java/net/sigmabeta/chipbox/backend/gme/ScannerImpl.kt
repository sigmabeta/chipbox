package net.sigmabeta.chipbox.backend.gme

import net.sigmabeta.chipbox.backend.Backend
import net.sigmabeta.chipbox.backend.Scanner

class ScannerImpl : Scanner {
    override fun getBackendId() = Backend.ID_GME

    external override fun getPlatform(path: String): String

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