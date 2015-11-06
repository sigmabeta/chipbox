package net.sigmabeta.chipbox.util.external

external fun getPlatformNative(path: String): String?

external fun getFileInfoNative(path: String, trackNumber: Int): Array<String>?