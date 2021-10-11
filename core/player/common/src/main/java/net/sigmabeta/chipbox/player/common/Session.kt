package net.sigmabeta.chipbox.player.common

import kotlin.random.Random

data class Session(
    val type: SessionType,
    val contentId: Long,
    val startingTrackId: Long? = null,
    val startingPosition: Int? = null,
    val currentPosition: Int? = null,
    val id: Long = Random.nextLong()
)