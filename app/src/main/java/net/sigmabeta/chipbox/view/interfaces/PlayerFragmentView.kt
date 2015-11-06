package net.sigmabeta.chipbox.view.interfaces

interface PlayerFragmentView {
    fun setTrackTitle(title: String)

    fun setGameTitle(title: String)

    fun setArtist(artist: String)

    fun setGameBoxart(gameId: Long)
}