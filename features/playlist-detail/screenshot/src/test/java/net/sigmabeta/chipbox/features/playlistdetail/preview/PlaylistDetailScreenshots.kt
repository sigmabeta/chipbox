package net.sigmabeta.chipbox.features.playlistdetail.preview

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
class PlaylistDetailScreenshots(
    private val deviceConfig: DeviceConfig,
    private val category: String,
    private val width: String,
    private val height: String,
) {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun playlistDetailScreen() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            PlaylistDetail(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun playlistDetailScreenEmpty() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            PlaylistDetailEmpty(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun playlistDetailScreenEditing() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            PlaylistDetailEditing(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun playlistDetailScreenRenaming() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            PlaylistDetailRenaming(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun playlistDetailScreenConfirmingDelete() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            PlaylistDetailConfirmingDelete(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun playlistDetailScreenNotFound() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            PlaylistDetailNotFound(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = SUFFIX_TESTNAME)
        fun getDeviceConfig(): Iterable<Array<Any>> = INTERESTING_DEVICES
    }
}
