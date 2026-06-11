package net.sigmabeta.chipbox.utils

// RSN files are solid RAR archives bundling a game's SPC rips. There is no RAR-native audio inside;
// each member is a standalone SPC that GME plays directly. These helpers are the single source of
// truth for which members are subsongs and in what order — shared by the scanner's RsnReader (which
// reads each SPC's tags at scan time) and the playback staging layer (which extracts the member at
// a given subsong index). Both must agree on ordering, hence one function.

/** File extension of an RSN archive itself. */
const val RSN_EXTENSION = "rsn"

/** File extension of an RSN archive's playable members. */
const val RSN_MEMBER_EXTENSION = "spc"

/**
 * The SPC members of an RSN archive [bytes], in stable subsong order (case-insensitive by name),
 * or null when the archive can't be read on this platform. Non-SPC members (info.txt, cover art, …)
 * and directory entries are dropped.
 */
fun rsnSpcMembers(bytes: ByteArray): List<RarEntry>? =
    unrar(bytes)
        ?.filter { entry ->
            entry.name.substringAfterLast('.', "").equals(RSN_MEMBER_EXTENSION, ignoreCase = true)
        }
        ?.sortedBy { it.name.lowercase() }
