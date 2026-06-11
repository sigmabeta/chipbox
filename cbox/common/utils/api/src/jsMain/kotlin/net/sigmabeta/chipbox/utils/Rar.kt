package net.sigmabeta.chipbox.utils

/**
 * JS has no bundled RAR decoder; RSN sets aren't supported on this (enforcement-only) target, so
 * callers see it as a decode miss.
 */
actual fun unrar(bytes: ByteArray): List<RarEntry>? = null
