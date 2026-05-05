package net.sigmabeta.chipbox.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game")
data class GameEntity(
    val title: String,
    val photoUrl: String?,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)