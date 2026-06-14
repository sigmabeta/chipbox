package net.sigmabeta.chipbox.uitest.harness

import net.sigmabeta.sage.logging.Hatchet

/** No-op [Hatchet] for the test graph — screens log freely; the harness drops it on the floor. */
object StubHatchet : Hatchet {
    override fun v(message: String) = Unit
    override fun d(message: String) = Unit
    override fun i(message: String) = Unit
    override fun w(message: String) = Unit
    override fun e(message: String) = Unit
    override fun log(severity: Int, message: String) = Unit
}
