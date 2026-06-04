package net.sigmabeta.chipbox.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** JS runs on a single event-loop thread, so emulator calls are already serialised. */
actual val emulatorDispatcher: CoroutineDispatcher = Dispatchers.Default
