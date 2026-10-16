package net.sigmabeta.chipbox.perfbench

import kotlinx.serialization.json.Json

/**
 * The one JSON dialect every perfbench result is written and read with.
 *
 * The `class` discriminator is what makes [Fingerprint] and [Axis] self-describing in the file, so a
 * `results.json` can be parsed back without out-of-band knowledge of which subclass is present.
 * Defaults are encoded, keeping the file stable to diff.
 */
val PerfbenchJson: Json = Json {
    prettyPrint = true
    classDiscriminator = "class"
    encodeDefaults = true
    ignoreUnknownKeys = true
}
