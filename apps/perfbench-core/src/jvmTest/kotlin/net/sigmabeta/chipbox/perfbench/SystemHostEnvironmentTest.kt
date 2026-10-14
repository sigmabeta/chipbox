package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemHostEnvironmentTest {
    @Test
    fun `parses the model name out of proc cpuinfo`() {
        val cpuinfo = """
            processor	: 0
            vendor_id	: GenuineIntel
            model name	: Intel(R) Core(TM) i7-9750H CPU @ 2.60GHz
            cpu MHz		: 2600.000
        """.trimIndent()

        assertEquals("Intel(R) Core(TM) i7-9750H CPU @ 2.60GHz", parseCpuModel(cpuinfo))
    }

    @Test
    fun `returns null when there is no model name line`() {
        assertNull(parseCpuModel("processor\t: 0\ncpu MHz\t\t: 2600.000"))
    }

    @Test
    fun `detects a usable fingerprint on the running host`() {
        val environment = SystemHostEnvironment.detect()

        assertTrue(environment.cpuModel.isNotBlank())
        assertTrue(environment.cpuCores > 0)
        assertTrue(environment.os.isNotBlank())
        assertTrue(environment.kernel.isNotBlank())
        assertTrue(environment.jvm.isNotBlank())
    }
}
