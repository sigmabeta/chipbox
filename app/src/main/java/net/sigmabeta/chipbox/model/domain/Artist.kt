package net.sigmabeta.chipbox.model.domain

data class Artist(val id: Long, val name: String) {
    companion object {
        val ARTIST_ALL = -1L
    }
}