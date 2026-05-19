package net.sigmabeta.chipbox.player.common

/**
 * Observational snapshot of a [VolumeProcessor]'s gain state, surfaced on the debug
 * PlaybackStatus screen. [targetGain] is the product of every registered modification
 * (what the processor is chasing); [actualGain] is the smoothed value currently applied,
 * ramping toward the target at [VolumeProcessor.MAX_GAIN_CHANGE_PER_FRAME] per frame.
 * [modifications] is a copy of the keyed multipliers (duck / master / normalization / …).
 */
data class VolumeDebugInfo(
    val targetGain: Double,
    val actualGain: Double,
    val maxGain: Double,
    val modifications: Map<String, Double>,
)
