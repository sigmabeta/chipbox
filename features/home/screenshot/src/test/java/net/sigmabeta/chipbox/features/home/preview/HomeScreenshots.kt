package net.sigmabeta.chipbox.features.home.preview

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
class HomeScreenshots(
    private val deviceConfig: DeviceConfig,
    private val category: String,
    private val width: String,
    private val height: String,
) {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun homeScreen() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            Home(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun homeScreenLoading() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            HomeLoading(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun homeScreenEmptyNoFolders() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            HomeEmptyNoFolders(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun homeScreenEmptyFoldersPresent() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            HomeEmptyFoldersPresent(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun homeScreenScanStatusScanning() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            HomeScanStatusScanning(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun homeScreenScanStatusComplete() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            HomeScanStatusComplete(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun homeScreenScanStatusFailed() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            HomeScanStatusFailed(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = SUFFIX_TESTNAME)
        fun getDeviceConfig(): Iterable<Array<Any>> = INTERESTING_DEVICES
    }
}
