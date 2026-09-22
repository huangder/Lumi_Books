package com.huangder.lumibooks.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebdavFailureClassifierTest {

    @Test
    fun `http status codes map to their meaning`() {
        assertEquals(WebdavErrorKind.AUTH, WebdavFailureClassifier.kindForStatus(401))
        assertEquals(WebdavErrorKind.FORBIDDEN, WebdavFailureClassifier.kindForStatus(403))
        assertEquals(WebdavErrorKind.NOT_FOUND, WebdavFailureClassifier.kindForStatus(404))
        assertEquals(WebdavErrorKind.NOT_SUPPORTED, WebdavFailureClassifier.kindForStatus(405))
        assertEquals(WebdavErrorKind.NOT_SUPPORTED, WebdavFailureClassifier.kindForStatus(501))
        assertEquals(WebdavErrorKind.SERVER_ERROR, WebdavFailureClassifier.kindForStatus(503))
        assertEquals(WebdavErrorKind.UNKNOWN, WebdavFailureClassifier.kindForStatus(409))
    }

    @Test
    fun `each kind maps to its own user-facing bucket`() {
        assertEquals(
            WebdavFailureCategory.AUTH,
            WebdavFailureClassifier.classify(WebdavErrorKind.AUTH, 401)
        )
        assertEquals(
            WebdavFailureCategory.FORBIDDEN,
            WebdavFailureClassifier.classify(WebdavErrorKind.FORBIDDEN, 403)
        )
        assertEquals(
            WebdavFailureCategory.NOT_FOUND,
            WebdavFailureClassifier.classify(WebdavErrorKind.NOT_FOUND, 404)
        )
        assertEquals(
            WebdavFailureCategory.CONFLICT,
            WebdavFailureClassifier.classify(WebdavErrorKind.CONFLICT, 409)
        )
        assertEquals(
            WebdavFailureCategory.NOT_SUPPORTED,
            WebdavFailureClassifier.classify(WebdavErrorKind.NOT_SUPPORTED, 501)
        )
        assertEquals(
            WebdavFailureCategory.SERVER_ERROR,
            WebdavFailureClassifier.classify(WebdavErrorKind.SERVER_ERROR, 500)
        )
        assertEquals(
            WebdavFailureCategory.NETWORK,
            WebdavFailureClassifier.classify(WebdavErrorKind.NETWORK, null)
        )
        assertEquals(
            WebdavFailureCategory.TIMEOUT,
            WebdavFailureClassifier.classify(WebdavErrorKind.TIMEOUT, null)
        )
        assertEquals(
            WebdavFailureCategory.TLS,
            WebdavFailureClassifier.classify(WebdavErrorKind.TLS, null)
        )
        assertEquals(
            WebdavFailureCategory.INVALID_URL,
            WebdavFailureClassifier.classify(WebdavErrorKind.INVALID_URL, null)
        )
        assertEquals(
            WebdavFailureCategory.REDIRECT,
            WebdavFailureClassifier.classify(WebdavErrorKind.REDIRECT, 302)
        )
        assertEquals(
            WebdavFailureCategory.INVALID_RESPONSE,
            WebdavFailureClassifier.classify(WebdavErrorKind.INVALID_RESPONSE, 200)
        )
    }

    @Test
    fun `unclassified exceptions still fall back to the http status code`() {
        // This is the path that used to render the generic "WebDAV request failed".
        assertEquals(
            WebdavFailureCategory.FORBIDDEN,
            WebdavFailureClassifier.classify(WebdavErrorKind.UNKNOWN, 403)
        )
        assertEquals(
            WebdavFailureCategory.AUTH,
            WebdavFailureClassifier.classify(WebdavErrorKind.UNKNOWN, 401)
        )
        assertEquals(
            WebdavFailureCategory.UNKNOWN,
            WebdavFailureClassifier.classify(WebdavErrorKind.UNKNOWN, 409)
        )
        assertEquals(
            WebdavFailureCategory.UNKNOWN,
            WebdavFailureClassifier.classify(WebdavErrorKind.UNKNOWN, null)
        )
    }

    @Test
    fun `quota server code wins over the transport classification`() {
        assertEquals(
            WebdavFailureCategory.QUOTA,
            WebdavFailureClassifier.classify(
                kind = WebdavErrorKind.SERVER_ERROR,
                statusCode = 403,
                serverCode = WebdavFailureClassifier.QUOTA_SERVER_CODE
            )
        )
    }

    @Test
    fun `only 2xx counts as a successful probe`() {
        assertTrue(WebdavFailureClassifier.isSuccessStatus(200))
        assertTrue(WebdavFailureClassifier.isSuccessStatus(207))
        assertFalse(WebdavFailureClassifier.isSuccessStatus(301))
        assertFalse(WebdavFailureClassifier.isSuccessStatus(404))
        assertFalse(WebdavFailureClassifier.isSuccessStatus(500))
    }
}
