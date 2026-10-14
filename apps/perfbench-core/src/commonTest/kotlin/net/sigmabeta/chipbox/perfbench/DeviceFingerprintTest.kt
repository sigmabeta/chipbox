package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceFingerprintTest {
    @Test
    fun `builds a device fingerprint from probed facts and commits`() {
        val environment = DeviceEnvironment(
            manufacturer = "Google",
            model = "Pixel 8",
            androidBuild = "AP1A.240505.004",
            abi = "arm64-v8a",
            thermalStatus = "none",
        )

        val fingerprint = deviceFingerprint(environment, chipboxCommit = "abc1234", sageCommit = "def5678")

        assertEquals("Google", fingerprint.manufacturer)
        assertEquals("Pixel 8", fingerprint.model)
        assertEquals("AP1A.240505.004", fingerprint.androidBuild)
        assertEquals("arm64-v8a", fingerprint.abi)
        assertEquals("none", fingerprint.thermalStatus)
        assertEquals("abc1234", fingerprint.chipboxCommit)
        assertEquals("def5678", fingerprint.sageCommit)
    }
}
