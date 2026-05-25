package net.sigmabeta.chipbox.player.cache

/**
 * On-disk layout for a `.pcm` cache file.
 *
 * Header is a fixed [HEADER_SIZE_BYTES]-byte block written little-endian, followed by the body
 * — interleaved L/R `int16` PCM samples at the header's declared sample rate. The header is
 * written twice during a track's life: once at file creation with [COMPLETION_IN_PROGRESS] and
 * a placeholder frame count, then once at successful completion with [COMPLETION_COMPLETE] and
 * the final frame count. Readers refuse files in any other state.
 *
 * The reserved bytes at the tail leave room to extend the header without bumping [VERSION] for
 * additive fields.
 */
object PcmCacheFormat {
    /** Big-endian "CBPC" — easy to spot in a hex dump. */
    const val MAGIC: Int = 0x43425043

    const val VERSION: Int = 1

    /** Always 2 — every emulator emits interleaved stereo. */
    const val CHANNELS: Short = 2

    /** Always 16 — every emulator emits signed 16-bit. */
    const val BITS_PER_SAMPLE: Short = 16

    const val COMPLETION_IN_PROGRESS: Int = 0
    const val COMPLETION_COMPLETE: Int = 1

    const val HEADER_SIZE_BYTES: Int = 128

    /** Length of the source-hash field; matches a 64-bit FNV-1a hash rendered as 16 hex chars
     *  with room to spare for a longer hash if we ever switch. */
    const val SOURCE_HASH_FIELD_BYTES: Int = 16

    /** Bytes per stereo 16-bit frame. */
    const val BYTES_PER_FRAME: Int = 4
}
