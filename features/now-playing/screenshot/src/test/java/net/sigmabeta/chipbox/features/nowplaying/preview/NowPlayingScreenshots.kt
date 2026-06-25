package net.sigmabeta.chipbox.features.nowplaying.preview

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
class NowPlayingScreenshots(
    private val deviceConfig: DeviceConfig,
    private val category: String,
    private val width: String,
    private val height: String,
) {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun nowPlayingPlaying() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            NowPlayingPlaying(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun nowPlayingPaused() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            NowPlayingPaused(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun nowPlayingBuffering() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            NowPlayingBuffering(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun nowPlayingError() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            NowPlayingError(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun nowPlayingLinks() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            NowPlayingLinks(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun nowPlayingTagDetail() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            NowPlayingTagDetail(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun nowPlayingArtists() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            NowPlayingArtists(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun nowPlayingControls() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            NowPlayingControls(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    @Test
    fun nowPlayingControlsFavorited() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig)
        paparazzi.snapshot {
            NowPlayingControlsFavorited(syntheticWidthClass = deviceConfig.toWidthClass())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = SUFFIX_TESTNAME)
        fun getDeviceConfig(): Iterable<Array<Any>> = INTERESTING_DEVICES
    }
}
