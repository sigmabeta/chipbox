package net.sigmabeta.chipbox.features.gamesforplatform

import kotlinx.serialization.Serializable
import net.sigmabeta.chipbox.models.Platform

@Serializable
data class GamesForPlatform(val platform: Platform)
