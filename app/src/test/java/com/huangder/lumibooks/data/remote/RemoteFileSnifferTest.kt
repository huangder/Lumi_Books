package com.huangder.lumibooks.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFileSnifferTest {
    @Test
    fun kindleDownloadsRequireAPalmDatabaseSignature() {
        val header = ByteArray(78)
        "BOOKMOBI".toByteArray(Charsets.US_ASCII).copyInto(header, 60)
        for (extension in listOf("mobi", "azw", "azw3")) {
            assertTrue(RemoteFileSniffer.matches(header, extension))
            assertFalse(RemoteFileSniffer.matches("<html>Login required</html>".toByteArray(), extension))
            assertFalse(RemoteFileSniffer.matches(header.copyOf(60), extension))
        }
        assertFalse(RemoteFileSniffer.matches(header, "kfx"))
    }
}
