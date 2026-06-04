package net.sigmabeta.chipbox.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/** JVM/Android: one daemon thread that all native-emulator calls are pinned to. */
actual val emulatorDispatcher: CoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "chipbox-emulator").apply { isDaemon = true }
    }.asCoroutineDispatcher()
