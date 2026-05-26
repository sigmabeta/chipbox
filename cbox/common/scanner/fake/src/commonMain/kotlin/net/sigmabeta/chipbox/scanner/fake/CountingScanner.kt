package net.sigmabeta.chipbox.scanner.fake

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.scanner.Scanner

/**
 * Test [Scanner] subclass with a no-op `scan()` body that just bumps [startCount]. Production
 * [Scanner.startScan] is `final` and launches `scan()` into its own scope, so the count of
 * `scan()` invocations is the only externally observable signal that `startScan` was called.
 *
 * Pair with `Dispatchers.setMain(UnconfinedTestDispatcher)` so `startScan()`'s internal launch
 * runs synchronously on the test thread — the count is then visible the instant the caller
 * returns from its action handler.
 */
class CountingScanner(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Scanner(dispatcher = dispatcher) {

    var startCount: Int = 0

    override suspend fun CoroutineScope.scan() {
        startCount++
    }
}
