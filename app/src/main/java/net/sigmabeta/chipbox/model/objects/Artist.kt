package net.sigmabeta.chipbox.model.objects

data class Artist(val id: Long, val name: String) {
    companion object {
        val ARTIST_ALL = -1L
    }
}