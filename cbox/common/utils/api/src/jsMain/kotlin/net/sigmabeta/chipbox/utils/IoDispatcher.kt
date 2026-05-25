package net.sigmabeta.chipbox.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** JS has no dedicated I/O pool (single-threaded event loop), so fall back to Default. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
