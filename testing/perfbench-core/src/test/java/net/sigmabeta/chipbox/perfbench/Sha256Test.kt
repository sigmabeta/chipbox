package net.sigmabeta.chipbox.perfbench

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    @Test
    fun `hashes a corpus file to its lowercase hex sha256`() {
        val file = tempFile("chipbox perfbench corpus file\n".encodeToByteArray())

        assertEquals(
            "af876580885f32d0c09a65d9683f16853b175ac9a4744ee3698ce45db2caa193",
            sha256Hex(file.path),
        )
    }

    @Test
    fun `streams a file larger than the hash buffer to the same digest`() {
        val file = tempFile(ByteArray(20_000) { (it % 251).toByte() })

        assertEquals(
            "93a6015a3874a774dd59fdd5db19414b301525381eb5ddcc265cdcc68bb9d350",
            sha256Hex(file.path),
        )
    }

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("sha256", ".bin").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
}
