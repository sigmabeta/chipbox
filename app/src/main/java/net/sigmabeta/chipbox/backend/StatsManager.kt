package net.sigmabeta.chipbox.backend

import net.sigmabeta.chipbox.model.audio.AudioConfig
import timber.log.Timber

class StatsManager(val audioConfig: AudioConfig) {
    val times = LongArray(STATS_COUNT)

    var index = 0

    var underrunCount = 0

    fun recordTime(timeInMillis: Long) {
        times[index] = timeInMillis
        val difference = timeInMillis - getLastTime()

        // Initial condition.
        if (difference == timeInMillis) {
            incrementIndex()
            return
        }

        if (index == 0) {
            if (difference > audioConfig.minimumLatency) {
                Timber.e("Buffer underrun: Took %d ms to fill a %d ms buffer", difference, audioConfig.minimumLatency)
                underrunCount += 1
            }
        }

        incrementIndex()
    }

    fun clear() {
        this.index = 0

        underrunCount = 0

        for (index in 0..STATS_COUNT - 1) {
            times[index] = 0
        }
    }

    private fun getLastTime(): Long {
        var lastIndex: Int

        if (index == 0) {
            lastIndex = STATS_COUNT - 1
        } else {
            lastIndex = index - 1
        }

        return times[lastIndex]
    }

    private fun incrementIndex() {
        index += 1

        if (index >= STATS_COUNT) {
            index = 0
        }
    }

    companion object {
        val STAT_TYPES = arrayOf(
            "Time Reading Samples",
            "Time Writing Samples"
        )

        val STATS_COUNT = STAT_TYPES.size
    }
}