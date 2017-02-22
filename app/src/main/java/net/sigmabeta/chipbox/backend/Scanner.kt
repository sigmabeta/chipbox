package net.sigmabeta.chipbox.backend

interface Scanner {
    fun getBackendId(): Int

    fun getPlatform(path: String): String

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

        val EXTENSIONS_MUSIC = hashMapOf<String, Scanner>(
                Pair<String, Scanner>("spc", GME),
                Pair<String, Scanner>("vgm", GME),
                Pair<String, Scanner>("vgz", GME),
                Pair<String, Scanner>("nsf", GME),
                Pair<String, Scanner>("nsfe", GME),
                Pair<String, Scanner>("gbs", GME)
        )
    }
}