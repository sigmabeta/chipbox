package net.sigmabeta.chipbox.features.errorlog.real

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ErrorLogViewModel] reads the injected [net.sigmabeta.sage.logging.Hatchet]'s `recentErrors`
 * snapshot once in its `init` and folds it (newest-first) into `state.errors`. [BluntHatchet]
 * records every `e(...)`/`log(>=ERROR)` call into that same ring buffer, so it doubles as the
 * test fixture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ErrorLogViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state errors is empty when the hatchet has recorded nothing`() = runTest {
        val vm = ErrorLogViewModel(stubStringProvider(), BluntHatchet())
        assertTrue(vm.state.first().errors.isEmpty())
    }

    @Test
    fun `state errors exposes recorded errors newest-first`() = runTest {
        val hatchet = BluntHatchet()
        hatchet.e("first")
        hatchet.e("second")
        hatchet.e("third")

        val vm = ErrorLogViewModel(stubStringProvider(), hatchet)

        val messages = vm.state.first().errors.map { it.message }
        assertEquals(listOf("third", "second", "first"), messages)
    }

    @Test
    fun `state errors is a one-shot snapshot taken at construction`() = runTest {
        val hatchet = BluntHatchet()
        hatchet.e("before")

        val vm = ErrorLogViewModel(stubStringProvider(), hatchet)
        // Errors logged after the VM is built aren't reflected — recentErrors is read once in init.
        hatchet.e("after")

        val messages = vm.state.first().errors.map { it.message }
        assertEquals(listOf("before"), messages)
    }

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
