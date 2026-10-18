package net.sigmabeta.chipbox.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The server has no authentication in v1, so binding beyond loopback turns it into an open library
 * for the whole network. [ServerConfig] still allows it (trusted-LAN deployments need it) but the
 * startup path warns; this predicate is what decides whether to warn.
 */
class ServerConfigTest {

    @Test
    fun `loopback hosts are recognised`() {
        assertTrue(ServerConfig.isLoopbackHost("localhost"))
        assertTrue(ServerConfig.isLoopbackHost("127.0.0.1"))
        assertTrue(ServerConfig.isLoopbackHost("127.1.2.3"))
        assertTrue(ServerConfig.isLoopbackHost("::1"))
        assertTrue(ServerConfig.isLoopbackHost("[::1]"))
    }

    @Test
    fun `wildcard, LAN and hostnames are treated as non-loopback`() {
        assertFalse(ServerConfig.isLoopbackHost("0.0.0.0"))
        assertFalse(ServerConfig.isLoopbackHost("::"))
        assertFalse(ServerConfig.isLoopbackHost("192.168.1.20"))
        assertFalse(ServerConfig.isLoopbackHost("chipbox.lan"))
    }
}
