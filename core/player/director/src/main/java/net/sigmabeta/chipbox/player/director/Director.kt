package net.sigmabeta.chipbox.player.director

import net.sigmabeta.chipbox.player.common.Session

interface Director {
    fun start(session: Session)

    fun play()

    fun pause()

    fun stop()
}