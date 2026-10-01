package com.huangder.lumibooks.data.sync

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTargetClassifierTest {
    @Test
    fun extractsUrlHosts() {
        assertEquals("nas.local", LocalNetworkTargetClassifier.hostFromUrl("https://nas.local/dav"))
        assertEquals("fd00::1", LocalNetworkTargetClassifier.hostFromUrl("http://[fd00::1]:8080/dav"))
        assertEquals(null, LocalNetworkTargetClassifier.hostFromUrl("not a url"))
    }

    @Test
    fun classifiesLocalNamesAndIpv4Ranges() {
        listOf(
            "nas", "nas.local", "10.0.0.1", "172.16.1.2", "172.31.255.254",
            "192.168.50.2", "100.64.0.1", "100.127.255.254", "169.254.10.1"
        ).forEach { host ->
            assertTrue(host, LocalNetworkTargetClassifier.requiresLocalNetworkAccess(host))
        }
        listOf("example.com", "8.8.8.8", "172.32.0.1", "100.128.0.1").forEach { host ->
            assertFalse(host, LocalNetworkTargetClassifier.requiresLocalNetworkAccess(host))
        }
    }

    @Test
    fun classifiesLocalIpv6Ranges() {
        listOf("::1", "fe80::1", "fc00::1", "fd12:3456::1", "fec0::1").forEach { host ->
            assertTrue(host, LocalNetworkTargetClassifier.requiresLocalNetworkAccess(host))
        }
        assertFalse(LocalNetworkTargetClassifier.requiresLocalNetworkAccess("2001:4860:4860::8888"))
    }

    @Test
    fun resolvedPrivateAddressMakesPublicLookingHostLocal() {
        val privateAddress = InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 20))
        assertTrue(
            LocalNetworkTargetClassifier.requiresLocalNetworkAccess(
                host = "dav.example.com",
                resolvedAddresses = listOf(privateAddress)
            )
        )
        assertFalse(
            LocalNetworkTargetClassifier.requiresLocalNetworkAccess(
                host = "dav.example.com",
                resolvedAddresses = emptyList()
            )
        )
    }

    @Test
    fun permissionStateOnlyRequiresAndroid17PermissionForLocalTargets() {
        assertEquals(
            LocalNetworkAccessState.NOT_REQUIRED,
            localNetworkAccessState(true, sdkInt = 36, permissionGranted = false)
        )
        assertEquals(
            LocalNetworkAccessState.PERMISSION_REQUIRED,
            localNetworkAccessState(true, sdkInt = 37, permissionGranted = false)
        )
        assertEquals(
            LocalNetworkAccessState.GRANTED,
            localNetworkAccessState(true, sdkInt = 37, permissionGranted = true)
        )
        assertEquals(
            LocalNetworkAccessState.NOT_REQUIRED,
            localNetworkAccessState(false, sdkInt = 37, permissionGranted = false)
        )
    }
}
