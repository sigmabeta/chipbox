package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    val query: String,
    val timeMs: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)
