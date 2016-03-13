package net.sigmabeta.chipbox.model.objects

data class Game(
        val id: Long,
        val title: String,
        val platform: Int,
        val artLocal: String?,
        val artWeb: String?,
        val company: String?
)