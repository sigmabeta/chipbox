package net.sigmabeta.chipbox.util

fun ByteArray.convert(): String {
    return toString(charset("UTF-8"))
}
