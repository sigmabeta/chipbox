package net.sigmabeta.chipbox.player.common

import kotlin.random.Random

data class Session(
    val type: SessionType,
    val contentId: Long,
    val startingPosition: Int,
    val currentPosition: Int,
    val id: Long = Random.nextLong()
)