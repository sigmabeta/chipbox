package net.sigmabeta.chipbox.util.external

external fun getPlatformNative(path: String): String

external fun fileInfoSetupNative(path: String): String?

external fun fileInfoSetTrackNumberNative(trackNumber: Int)

external fun fileInfoTeardownNative()

external fun getFileTrackLength(): Long

external fun getFileIntroLength(): Long

external fun getFileLoopLength(): Long

external fun getFileTitle(): ByteArray

external fun getFileGameTitle(): ByteArray

external fun getFilePlatform(): ByteArray

external fun getFileArtist(): ByteArray