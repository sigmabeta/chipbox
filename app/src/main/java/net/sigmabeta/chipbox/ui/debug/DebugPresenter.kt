package net.sigmabeta.chipbox.ui.debug

import android.os.Bundle
import net.sigmabeta.chipbox.model.audio.AudioConfig
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.UiState
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

    override fun onTempDestroy() = Unit

    /**
     * BasePresenter
     */

    override fun onClick(id: Int) = Unit


    override fun setup(arguments: Bundle?) {
        state = UiState.READY
    }

    override fun teardown() = Unit

    override fun showReadyState() {
        view?.showBufferSize(audioConfig.minBufferSizeBytes, audioConfig.bufferSizeMultiplier)
        view?.showBufferCount(audioConfig.bufferCount)
        view?.showMinimumLatency(audioConfig.minimumLatency)
        view?.showActualLatency(audioConfig.singleBufferLatency)
        view?.showTotalBufferSize(audioConfig.totalBufferSizeMs)
        view?.showSampleRate(audioConfig.sampleRate)
    }
}