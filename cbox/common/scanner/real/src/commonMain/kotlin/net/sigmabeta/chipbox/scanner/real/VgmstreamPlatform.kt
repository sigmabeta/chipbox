package net.sigmabeta.chipbox.scanner.real

import net.sigmabeta.chipbox.models.Platform

/**
 * Best-effort platform for a file decoded by vgmstream, inferred from its [extension].
 *
 * vgmstream's native probe reports sample rate, length and stream name but not the source console,
 * so files routed through it would otherwise all land in [Platform.OTHER]. These extensions are
 * unambiguous PlayStation formats in vgmstream's format set (CD-XA audio, PS-ADPCM VAG, VAB sound
 * banks, SEQ sequences), so map them to [Platform.PSX]; anything else stays [Platform.OTHER]. This
 * is metadata only — it never affects which decoder plays the file.
 */
internal fun platformForVgmstreamExtension(extension: String): Platform = when (extension.lowercase()) {
    "xa", "vag", "vb", "vh", "vab", "seq", "vpk" -> Platform.PSX
    else -> Platform.OTHER
}
