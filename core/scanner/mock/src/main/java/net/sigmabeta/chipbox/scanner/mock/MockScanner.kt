package net.sigmabeta.chipbox.scanner.mock

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.scanner.Scanner

class MockScanner(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Scanner {

}