package net.sigmabeta.chipbox.features.home.module

import kotlin.time.Clock

/**
 * The day Home's date-seeded rows are picked for, as a UTC epoch day.
 *
 * [System] reads the clock, so "Game of the day" rolls over at 00:00 UTC. The device benchmark's
 * build binds a [fixed] day instead: a pick that changes overnight would make each night's Home a
 * different workload from the baseline it is compared against.
 *
 * Injected with [System] as the default, so only a graph that wants a different day binds one.
 */
fun interface HomeDay {
    fun epochDay(): Long

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L

        /** Today, from the system clock: the shipped behaviour. */
        val System: HomeDay = HomeDay { Clock.System.now().toEpochMilliseconds() / MILLIS_PER_DAY }

        /** Always [epochDay], whatever the date. */
        fun fixed(epochDay: Long): HomeDay = HomeDay { epochDay }
    }
}
