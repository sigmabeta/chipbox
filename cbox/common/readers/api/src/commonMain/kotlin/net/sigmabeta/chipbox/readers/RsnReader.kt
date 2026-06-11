package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.utils.rsnSpcMembers
import net.sigmabeta.sage.logging.Hatchet

/**
 * RSN files are solid RAR archives bundling a game's SPC rips (e.g. `4ns.rsn` → `4ns-01.spc` …).
 * GME ships no compiled-in RAR support, so this reader unpacks the archive in Kotlin and reads each
 * SPC's ID666/xid6 tags via [SpcReader], emitting one [RawTrack] per SPC. The subsong order matches
 * [rsnSpcMembers], so the playback staging layer extracts the same member for a given track number.
 */
class RsnReader(
    private val hatchet: Hatchet,
    private val spcReader: SpcReader,
) : Reader() {
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        val members = rsnSpcMembers(bytes)
        if (members == null) {
            hatchet.w("RSN parse failed: $identifier is not a readable RAR archive.")
            return null
        }
        if (members.isEmpty()) {
            hatchet.w("RSN parse failed: $identifier contains no SPC files.")
            return null
        }

        // Fallback game name when a member carries no game tag: the archive's own base filename.
        val archiveName = identifier.substringAfterLast('/').substringBeforeLast('.', identifier)

        return members.mapIndexed { index, member ->
            val tags = spcReader.readTags(member.bytes, member.name)
            RawTrack(
                path = identifier,
                source = "",
                title = tags?.songTitle?.takeUnless { it == TAG_UNKNOWN }
                    ?: deriveMetaFromFilename(member.name).title,
                artist = tags?.artistName ?: TAG_UNKNOWN,
                game = tags?.gameTitle?.takeUnless { it == TAG_UNKNOWN } ?: archiveName,
                length = tags?.trackLengthMs ?: LENGTH_UNKNOWN_MS,
                trackNumber = index,
                fadeLengthMs = tags?.fadeLengthMs ?: 0L,
                platform = Platform.SNES,
            )
        }
    }
}
