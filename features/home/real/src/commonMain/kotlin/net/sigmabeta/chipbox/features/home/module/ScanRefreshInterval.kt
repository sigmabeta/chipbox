package net.sigmabeta.chipbox.features.home.module

import kotlin.jvm.JvmInline

/**
 * How often the Home scan-status card re-renders while a scan runs, in milliseconds.
 *
 * The scanner's per-file heartbeat fires far too often to render each one, so production [sample]s
 * the reduced state down to this interval. A value of `0` (or less) disables the throttle — the card
 * emits on every scanner input — which UI tests use to keep the card in lockstep with the scan verbs
 * they drive (the `sample` timer otherwise makes assertions race the next sampled frame).
 *
 * Injected so each app graph can pick its cadence and the UI-test graph can pass `0`.
 */
@JvmInline
value class ScanRefreshInterval(val millis: Long) {
    companion object {
        /** Production cadence (ms): the scanner's per-file heartbeat is far faster than this. */
        const val PRODUCTION_MILLIS = 500L
    }
}
