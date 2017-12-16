package net.sigmabeta.chipbox.ui.debug

import android.os.Bundle
import net.sigmabeta.chipbox.model.audio.AudioConfig
import net.sigmabeta.chipbox.ui.ActivityPresenter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugPresenter @Inject constructor(val audioConfig: AudioConfig): ActivityPresenter<DebugView>() {

    /**
     * Public Methods
     */

    fun onBufferMultiplierChange(multiplier: Int) {
        audioConfig.bufferSizeMultiplier = multiplier

        view?.showActualLatency(audioConfig.singleBufferLatency)
        view?.showTotalBufferSize(audioConfig.totalBufferSizeMs)
    }

    fun onBufferCountChange(count: Int) {
        audioConfig.bufferCount = count

        view?.showActualLatency(audioConfig.singleBufferLatency)
        view?.showTotalBufferSize(audioConfig.totalBufferSizeMs)
    }

    /**
     * ActivityPresenter
     */

    override fun onReenter() = Unit

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onTempDestroy() = Unit

    /**
     * BasePresenter
     */

    override fun onClick(id: Int) = Unit


    override fun setup(arguments: Bundle?) {
        setupHelper()
    }

    override fun teardown() = Unit

    override fun showReadyState() {
        setupHelper()
    }

    /**
     * Private Methods
     */

    private fun setupHelper() {
        view?.showBufferSize(audioConfig.minBufferSizeBytes, audioConfig.bufferSizeMultiplier)
        view?.showBufferCount(audioConfig.bufferCount)
        view?.showMinimumLatency(audioConfig.minimumLatency)
        view?.showActualLatency(audioConfig.singleBufferLatency)
        view?.showTotalBufferSize(audioConfig.totalBufferSizeMs)
        view?.showSampleRate(audioConfig.sampleRate)
    }

}