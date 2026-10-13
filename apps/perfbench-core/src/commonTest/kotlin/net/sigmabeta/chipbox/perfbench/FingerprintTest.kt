package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals

class FingerprintTest {
    @Test
    fun `builds a host fingerprint from probed facts and commits`() {
        val environment = HostEnvironment(
            cpuModel = "Intel Core i7-9750H",
            cpuCores = 12,
            os = "Ubuntu 24.04",
            kernel = "6.8.0-31-generic",
            jvm = "OpenJDK 64-Bit Server VM 17.0.11",
        )

        val fingerprint = hostFingerprint(environment, chipboxCommit = "abc1234", sageCommit = "def5678")

        assertEquals("Intel Core i7-9750H", fingerprint.cpuModel)
        assertEquals(12, fingerprint.cpuCores)
        assertEquals("Ubuntu 24.04", fingerprint.os)
        assertEquals("6.8.0-31-generic", fingerprint.kernel)
        assertEquals("OpenJDK 64-Bit Server VM 17.0.11", fingerprint.jvm)
        assertEquals("abc1234", fingerprint.chipboxCommit)
        assertEquals("def5678", fingerprint.sageCommit)
    }

    @Test
    fun `scenario ids are stable and resolvable`() {
        assertEquals(listOf("audio.usf", "audio.vgm", "audio.psf"), AudioScenario.all.map { it.id })
        assertEquals(AudioScenario.PSF, AudioScenario.fromId("audio.psf"))
        assertEquals(null, AudioScenario.fromId("audio.spc"))
    }
}
