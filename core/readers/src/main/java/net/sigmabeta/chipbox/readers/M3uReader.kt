package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack

object M3uReader : Reader() {
    // TODO: m3u resolution requires sibling-URI lookup that isn't wired for SAF yet.
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? = null
}
