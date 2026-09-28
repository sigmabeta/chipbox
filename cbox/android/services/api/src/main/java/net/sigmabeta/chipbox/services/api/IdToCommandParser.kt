package net.sigmabeta.chipbox.services.api

import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.services.api.ChipboxPlaybackService.Companion.ID_ROOT
import net.sigmabeta.sage.logging.Hatchet

object IdToCommandParser {
    fun handleCommand(director: Director, mediaId: String, hatchet: Hatchet) {
        val details = mediaId.substringAfter(ID_ROOT)
        val detailSplit = details.split(".")

        val type = detailSplit[0]
        val parentId = detailSplit[1]
        val trackId = detailSplit[2]

        val sessionType = when (type) {
            LibraryBrowser.COMMAND_GAMES -> SessionType.GAME

            LibraryBrowser.COMMAND_ARTISTS -> SessionType.ARTIST

            LibraryBrowser.COMMAND_TRACKS -> SessionType.ALL_TRACKS

            else -> {
                hatchet.w("Unhandled media command type '$type' (mediaId=$mediaId); ignoring.")
                return
            }
        }

        // contentId is unused for ALL_TRACKS (per SessionType docs); parentId there is the
        // placeholder "top" segment.
        val contentId = if (sessionType == SessionType.ALL_TRACKS) 0L else parentId.toLong()

        // A "Shuffle all" item carries ID_SHUFFLE where a track id would be: start the same
        // setlist shuffled from its first slot, as the in-app Shuffle All CTA does.
        val session = if (trackId == LibraryBrowser.ID_SHUFFLE) {
            Session(sessionType, contentId, startingPosition = 0, shuffled = true)
        } else {
            Session(sessionType, contentId, startingTrackId = trackId.toLong())
        }

        director.request(SessionRequest.Start(session))
    }
}
