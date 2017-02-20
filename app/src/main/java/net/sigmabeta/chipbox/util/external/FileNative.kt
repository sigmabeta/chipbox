package net.sigmabeta.chipbox.util.external

external fun getPlatformNativeGme(path: String): String

external fun fileInfoSetupNativeGme(path: String): String?

external fun fileInfoGetTrackCountGme(): Int

external fun fileInfoSetTrackNumberNativeGme(trackNumber: Int)

external fun fileInfoTeardownNativeGme()

external fun getFileTrackLengthGme(): Long

external fun getFileIntroLengthGme(): Long

external fun getFileLoopLengthGme(): Long

external fun getFileTitleGme(): ByteArray

external fun getFileGameTitleGme(): ByteArray

external fun getFilePlatformGme(): ByteArray

external fun getFileArtistGme(): ByteArray