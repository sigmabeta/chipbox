package net.sigmabeta.chipbox.scanner.real

import net.sigmabeta.chipbox.models.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class VgmstreamPlatformTest {

    @Test
    fun `playstation formats map to PSX (case-insensitive)`() {
        for (ext in listOf("xa", "vag", "vb", "vh", "vab", "seq", "vpk", "XA", "VB", "SEQ")) {
            assertEquals(Platform.PSX, platformForVgmstreamExtension(ext), "expected PSX for .$ext")
        }
    }

    @Test
    fun `generic or non-playstation formats stay OTHER`() {
        for (ext in listOf("genh", "raw", "adpcm", "ape", "flac", "wav", "txtp", "")) {
            assertEquals(Platform.OTHER, platformForVgmstreamExtension(ext), "expected OTHER for .$ext")
        }
    }
}
