package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ResultArtifactTest {
    @Test
    fun `each axis gets its own result file named after it`() {
        assertEquals("results-ui.json", axisResultFileName("ui"))
        assertEquals("results-startup.json", axisResultFileName("startup"))
    }

    @Test
    fun `axis result files never collide with each other or the whole-run file`() {
        val ui = axisResultFileName("ui")
        val startup = axisResultFileName("startup")

        assertNotEquals(ui, startup)
        assertNotEquals(RESULTS_FILE_NAME, ui)
        assertNotEquals(RESULTS_FILE_NAME, startup)
    }
}
