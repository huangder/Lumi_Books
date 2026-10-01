package com.huangder.lumibooks.data.sync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class LocalNetworkAccessState {
    NOT_REQUIRED,
    GRANTED,
    PERMISSION_REQUIRED
}

/** Pure address classification used before any WebDAV request is made. */
object LocalNetworkTargetClassifier {
    fun hostFromUrl(url: String): String? = runCatching {
        URI(url.trim()).host
            ?.trim()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.trimEnd('.')
            ?.takeIf(String::isNotBlank)
    }.getOrNull()

    fun requiresLocalNetworkAccess(
        host: String,
        resolvedAddresses: Iterable<InetAddress> = emptyList()
    ): Boolean {
        val normalized = host.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .trimEnd('.')
            .lowercase()
        if (normalized.isBlank()) return false
        if (normalized.endsWith(".local")) return true
        if ('.' !in normalized && ':' !in normalized) return true
        if (parseLiteralAddress(normalized)?.let(::isLocalAddress) == true) return true
        return resolvedAddresses.any(::isLocalAddress)
    }

    fun isLocalAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress
        ) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 4) return isLocalIpv4(bytes)
        if (bytes.size != 16) return false

        // IPv6 unique-local fc00::/7 and deprecated site-local fec0::/10.
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        if ((first and 0xfe) == 0xfc || (first == 0xfe && (second and 0xc0) == 0xc0)) {
            return true
        }
        // IPv4-mapped IPv6 addresses use the final four bytes.
        val mappedIpv4 = bytes.take(10).all { it.toInt() == 0 } &&
            bytes[10].toInt() == -1 && bytes[11].toInt() == -1
        return mappedIpv4 && isLocalIpv4(bytes.copyOfRange(12, 16))
    }

    private fun isLocalIpv4(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return first == 10 ||
            first == 127 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 100 && second in 64..127) ||
            (first == 169 && second == 254)
    }

    private fun parseLiteralAddress(host: String): InetAddress? {
        val ipv4Parts = host.split('.')
        if (ipv4Parts.size == 4) {
            val bytes = ipv4Parts.map { part ->
                part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            }.map(Int::toByte).toByteArray()
            return InetAddress.getByAddress(bytes)
        }
        if (':' !in host) return null
        return runCatching {
            InetAddress.getByName(host.substringBefore('%'))
        }.getOrNull()
    }
}

internal fun localNetworkAccessState(
    requiresLocalNetworkAccess: Boolean,
    sdkInt: Int,
    permissionGranted: Boolean
): LocalNetworkAccessState = when {
    !requiresLocalNetworkAccess || sdkInt < LocalNetworkAccessManager.PERMISSION_SDK_INT ->
        LocalNetworkAccessState.NOT_REQUIRED
    permissionGranted -> LocalNetworkAccessState.GRANTED
    else -> LocalNetworkAccessState.PERMISSION_REQUIRED
}

@Singleton
class LocalNetworkAccessManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun stateFor(serverUrl: String): LocalNetworkAccessState {
        val host = LocalNetworkTargetClassifier.hostFromUrl(serverUrl)
            ?: return LocalNetworkAccessState.NOT_REQUIRED
        val knownLocal = LocalNetworkTargetClassifier.requiresLocalNetworkAccess(host)
        val resolved = if (knownLocal) {
            emptyList()
        } else {
            withTimeoutOrNull(DNS_TIMEOUT_MS) {
                runInterruptible(Dispatchers.IO) {
                    runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
                }
            }.orEmpty()
        }
        return localNetworkAccessState(
            requiresLocalNetworkAccess = knownLocal ||
                LocalNetworkTargetClassifier.requiresLocalNetworkAccess(host, resolved),
            sdkInt = Build.VERSION.SDK_INT,
            permissionGranted = hasPermission()
        )
    }

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < PERMISSION_SDK_INT ||
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        const val PERMISSION_SDK_INT = 37
        private const val DNS_TIMEOUT_MS = 3_000L
    }
}
