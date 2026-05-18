package net.sigmabeta.chipbox.features.gamesforplatform.preview

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import net.sigmabeta.chipbox.ui.previews.PreviewTestUtils.INTERESTING_DEVICES
import net.sigmabeta.chipbox.ui.previews.PreviewTestUtils.SUFFIX_TESTNAME
import net.sigmabeta.chipbox.ui.previews.toWidthClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GamesForPlatformScreenshots(
    private val deviceConfig: DeviceConfig,
    private val category: String,
    private val width: String,
    private val height: String,
) {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun gamesForPlatformScreen() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            GamesForPlatform(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun gamesForPlatformScreenLoading() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            GamesForPlatformLoading(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = SUFFIX_TESTNAME)
        fun getDeviceConfig(): Iterable<Array<Any>> = INTERESTING_DEVICES
    }
}
