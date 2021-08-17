package net.sigmabeta.chipbox.readers

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer


internal fun ByteBuffer.nextFourBytesAsInt() = int

internal fun ByteBuffer.nextFourBytesAsString(): String? {
    val headerArray = ByteArray(4)

    try {
        get(headerArray)
    } catch (ex: BufferUnderflowException) {
        return null
    }

    return headerArray.toString(Charsets.US_ASCII)
}