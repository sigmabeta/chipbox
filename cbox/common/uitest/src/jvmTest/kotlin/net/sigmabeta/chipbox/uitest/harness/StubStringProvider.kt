package net.sigmabeta.chipbox.uitest.harness

import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider

/**
 * [StringProvider] returning empty strings. The screen titles the harness asserts on come from
 * entity data ("Metal Slug", "Stage 1", "JIM"), not string resources, so the resource lookups can
 * be inert — which also sidesteps composeResources loading under runComposeUiTest.
 */
object StubStringProvider : StringProvider {
    override fun getString(string: SageStringId): String = ""
    override fun getStringOneArg(string: SageStringId, arg: String): String = ""
    override fun getStringOneInt(string: SageStringId, arg: Int): String = ""
    override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = ""
}
