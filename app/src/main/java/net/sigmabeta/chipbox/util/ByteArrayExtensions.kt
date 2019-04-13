package net.sigmabeta.chipbox.util

fun ByteArray.convert(): String {
    return toString(Charsets.ISO_8859_1)
}

fun ByteArray.convertUtf(): String {
    return toString(Charsets.UTF_8)
}
