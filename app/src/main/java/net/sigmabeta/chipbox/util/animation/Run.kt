package net.sigmabeta.chipbox.util.animation

import android.graphics.Rect

/**
 * Models the location of a run of text in both start and end states.
 */
data class Run internal constructor(internal val start: Rect, internal val startVisible: Boolean, internal val end: Rect, internal val endVisible: Boolean)
