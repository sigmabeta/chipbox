package net.sigmabeta.chipbox.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** JVM/Android: the real blocking-I/O thread pool. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
