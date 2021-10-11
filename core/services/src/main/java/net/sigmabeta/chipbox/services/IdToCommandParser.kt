package net.sigmabeta.chipbox.services

import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.services.ChipboxPlaybackService.Companion.ID_ROOT

object IdToCommandParser {
    fun parse(director: Director, mediaId: String) {
        val details = mediaId.substringAfter(ID_ROOT)
        val detailSplit = details.split(".")

        val type = detailSplit[0]
        val parentId = detailSplit[1]
        val trackId = detailSplit[2]

        when (type) {
            LibraryBrowser.COMMAND_GAMES -> director.start(
                Session(
                    SessionType.GAME,
                    parentId.toLong(),
                    startingTrackId = trackId.toLong()
                )
            )
            else -> TODO()
        }
    }

}