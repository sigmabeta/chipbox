package net.sigmabeta.chipbox.models

data class Game (
    val id: Long,
    val title: String,
    val photoUrl: String? = null
)