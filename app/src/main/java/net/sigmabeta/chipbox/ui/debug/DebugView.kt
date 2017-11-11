package net.sigmabeta.chipbox.ui.debug

import net.sigmabeta.chipbox.ui.BaseView

interface DebugView : BaseView {
    fun showBufferSize(minBytes: Int, multiplier: Int)

    fun showBufferCount(buffers: Int)

    fun showMinimumLatency(millis: Int)

    fun showActualLatency(millis: Int)

    fun showTotalBufferSize(millis: Int)

    fun showSampleRate(hertz: Int)
}