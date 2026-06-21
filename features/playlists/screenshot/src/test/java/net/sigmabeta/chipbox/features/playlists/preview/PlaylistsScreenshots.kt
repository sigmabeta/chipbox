package net.sigmabeta.chipbox.features.playlists.preview

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
class PlaylistsScreenshots(
    private val deviceConfig: DeviceConfig,
    private val category: String,
    private val width: String,
    private val height: String,
) {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun playlistsScreen() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            Playlists(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun playlistsScreenEmpty() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            PlaylistsEmpty(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun playlistsScreenPicker() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            PlaylistsPicker(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = SUFFIX_TESTNAME)
        fun getDeviceConfig(): Iterable<Array<Any>> = INTERESTING_DEVICES
    }
}
