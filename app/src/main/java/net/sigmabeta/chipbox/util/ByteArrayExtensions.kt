package net.sigmabeta.chipbox.util

fun ByteArray.convert(): String {
    return toString(Charsets.ISO_8859_1)
}
