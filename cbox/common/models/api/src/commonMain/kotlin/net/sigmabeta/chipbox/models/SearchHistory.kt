package net.sigmabeta.chipbox.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchHistory(
    val id: Long,
    val query: String,
)
