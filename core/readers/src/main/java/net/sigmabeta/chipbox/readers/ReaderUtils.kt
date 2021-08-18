package net.sigmabeta.chipbox.readers

import java.io.File
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder


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

internal fun String.orValidString(): String {
    if (this == "<?>") {
        return TAG_UNKNOWN
    }

    if (this.isEmpty()) {
        return TAG_UNKNOWN
    }

    return this
}

internal fun fileAsByteBuffer(path: String): ByteBuffer {
    val file = File(path)
    val buffer = ByteBuffer.wrap(
        file.readBytes(),
        0,
        file.length().toInt()
    )

    return buffer.order(ByteOrder.LITTLE_ENDIAN)
}

internal fun String.toLengthMillis(): Long {
    val splitText = split(":")

    val minutesText: String
    val secondsText: String

    when (splitText.size) {
        1 -> {
            minutesText = "0"
            secondsText = splitText[0]
        }
        2 -> {
            minutesText = splitText[0]
            secondsText = splitText[1]
        }
        3 -> {
            minutesText = splitText[1]
            secondsText = splitText[2]
        }
        else -> return 0L
    }

    val minutesInt = minutesText.toInt()
    val secondsInt = try {
        secondsText.toInt() + (minutesInt * 60)
    } catch (ex: NumberFormatException) {
        ((secondsText.toFloatOrNull() ?: 0.0f) + (minutesInt * 60)).toInt()
    }

    return secondsInt * 1000L
}

const val LENGTH_UNKNOWN_MS = -1L
const val TAG_UNKNOWN = "Unknown"