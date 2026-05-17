package net.sigmabeta.chipbox.utils

fun ByteArray.convert(): String = toString(Charsets.ISO_8859_1)

fun ByteArray.convertUtf(): String = toString(Charsets.UTF_8)
