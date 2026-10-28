package net.sigmabeta.chipbox.readers

/**
 * True when [magic] appears as ASCII bytes at [offset] in this array. A truncated or negative range
 * is simply a non-match — never a throw — so a header sniff can be written as one condition.
 */
fun ByteArray.hasAsciiAt(offset: Int, magic: String): Boolean {
    // `offset + magic.length` overflows Int near Int.MAX_VALUE, so compare against the remaining
    // length instead of summing.
    if (offset < 0 || offset > size - magic.length) return false
    for (i in magic.indices) {
        if (this[offset + i].toInt() != magic[i].code) return false
    }
    return true
}

/** True when this array starts with the ASCII bytes of [magic]. */
fun ByteArray.hasAsciiPrefix(magic: String): Boolean = hasAsciiAt(0, magic)

/**
 * True when [magic] appears as ASCII bytes at absolute [offset] in this reader's slice. Does not
 * move the cursor; a truncated or negative range is a non-match.
 */
internal fun ByteReader.hasAsciiAt(offset: Int, magic: String): Boolean {
    // See the ByteArray overload: compare against the remaining length so near-Int.MAX_VALUE
    // offsets cannot wrap the bounds check and index out of the slice.
    if (offset < 0 || offset > size - magic.length) return false
    for (i in magic.indices) {
        if (get(offset + i).toInt() != magic[i].code) return false
    }
    return true
}

/** True when this reader's slice starts with the ASCII bytes of [magic]; does not move the cursor. */
internal fun ByteReader.hasAsciiPrefix(magic: String): Boolean = hasAsciiAt(0, magic)
