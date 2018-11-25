package net.sigmabeta.chipbox.backend

interface Scanner {
    fun getBackendId(): Int

    fun fileInfoSetup(path: String): String?

    fun fileInfoGetTrackCount(): Int

    fun fileInfoSetTrackNumber(trackNumber: Int)

    fun fileInfoTeardown()

    fun getFileTrackLength(): Long

    fun getFileIntroLength(): Long

    fun getFileLoopLength(): Long

    fun getFileTitle(): ByteArray?

    fun getFileGameTitle(): ByteArray?

    fun getFilePlatform(): ByteArray?

    fun getFileArtist(): ByteArray?

    companion object {
        val GME = net.sigmabeta.chipbox.backend.gme.ScannerImpl()
        val VGM = net.sigmabeta.chipbox.backend.vgm.ScannerImpl()
        val PSF = net.sigmabeta.chipbox.backend.psf.ScannerImpl()

        val EXTENSIONS_MUSIC = hashMapOf<String, Scanner>(
                Pair<String, Scanner>("spc", GME),
                Pair<String, Scanner>("vgm", VGM),
                Pair<String, Scanner>("vgz", VGM),
                Pair<String, Scanner>("nsf", GME),
                Pair<String, Scanner>("nsfe", GME),
                Pair<String, Scanner>("gbs", GME),
                Pair<String, Scanner>("psf", PSF),
                Pair<String, Scanner>("minipsf", PSF)
        )
    }
}