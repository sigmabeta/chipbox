package net.sigmabeta.chipbox.utils

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher for blocking I/O work. kotlinx-coroutines exposes `Dispatchers.IO` only on
 * JVM/Native — not in the common stdlib — so this is an expect/actual: `Dispatchers.IO` on
 * JVM/Android, `Dispatchers.Default` elsewhere (JS/wasm have no dedicated I/O pool).
 */
expect val ioDispatcher: CoroutineDispatcher
