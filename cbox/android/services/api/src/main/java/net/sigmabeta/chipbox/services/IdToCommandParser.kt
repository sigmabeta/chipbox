package net.sigmabeta.chipbox.services

import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.services.ChipboxPlaybackService.Companion.ID_ROOT
import net.sigmabeta.sage.logging.Hatchet

object IdToCommandParser {
    fun handleCommand(director: Director, mediaId: String, hatchet: Hatchet) {
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
            LibraryBrowser.COMMAND_ARTISTS -> director.start(
                Session(
                    SessionType.ARTIST,
                    parentId.toLong(),
                    startingTrackId = trackId.toLong()
                )
            )
            else -> hatchet.w("Unhandled media command type '$type' (mediaId=$mediaId); ignoring.")
        }
    }

}