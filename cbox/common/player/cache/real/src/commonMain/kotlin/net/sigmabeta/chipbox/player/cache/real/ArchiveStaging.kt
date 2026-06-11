package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.utils.RSN_EXTENSION
import net.sigmabeta.chipbox.utils.RSN_MEMBER_EXTENSION

// Archive formats (RSN) carry no playable bytes of their own: stageTrack unpacks the member at a
// track's subsong index into a plain emulator file, so emulator selection, staging, and the GME
// track number all key off the *member's* format rather than the archive's. These two helpers keep
// the scanner-side ordering (rsnSpcMembers) and the playback side in lockstep.

/**
 * The extension whose emulator actually plays [ext]'s bytes once staged. RSN archives are unpacked
 * to a single SPC at stage time, so they play through the SPC emulator; everything else plays as
 * itself.
 */
internal fun playbackExtension(ext: String): String =
    if (ext == RSN_EXTENSION) RSN_MEMBER_EXTENSION else ext

/**
 * The track as the emulator should see it after staging. An RSN's [Track.trackNumber] is the
 * archive subsong index used to pick the SPC member; the extracted SPC is itself a single-track
 * file, so the emulator must start track 0. Non-archive tracks are unchanged.
 */
internal fun Track.asStagedPlaybackTrack(): Track =
    if (extension == RSN_EXTENSION) copy(trackNumber = 0) else this
