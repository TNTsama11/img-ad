package com.imgad.data.remote

import com.imgad.domain.model.GenerationFailure
import com.imgad.domain.model.RemoteErrorKind
import com.imgad.domain.model.RemoteGenerationError
import java.net.InetAddress
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Dns

class RemoteUrlPolicy(
    private val allowInsecureHttp: Boolean = false,
    private val allowLoopback: Boolean = false,
) {
    fun validate(url: String, rejectQueryFragment: Boolean = false): HttpUrl {
        val parsed = url.toHttpUrlOrNull()
            ?: throw configFailure("Invalid remote URL")
        if (parsed.scheme == "http" && !allowInsecureHttp) {
            throw configFailure("Insecure HTTP is disabled")
        }
        if (parsed.scheme !in setOf("http", "https")) {
            throw configFailure("Only HTTP(S) URLs are supported")
        }
        if (rejectQueryFragment && (parsed.query != null || parsed.fragment != null)) {
            throw configFailure("Remote base URL cannot contain query or fragment")
        }
        if (!allowLoopback && parsed.host.equals("localhost", ignoreCase = true)) {
            throw configFailure("Loopback hosts are disabled")
        }
        parseIpLiteral(parsed.host)?.let { validateAddresses(parsed.host, listOf(it)) }
        return parsed
    }

    private fun parseIpLiteral(host: String): InetAddress? {
        if (':' in host) return runCatching { InetAddress.getByName(host) }.getOrNull()
        val parts = host.split('.')
        if (parts.size != 4) return null
        val bytes = parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null }
            .map(Int::toByte)
            .toByteArray()
        return InetAddress.getByAddress(bytes)
    }

    internal fun validateAddresses(host: String, addresses: List<InetAddress>) {
        if (addresses.any(::isForbiddenAddress)) {
            throw configFailure("Private remote addresses are disabled")
        }
    }

    private fun isForbiddenAddress(address: InetAddress): Boolean =
        (address.isLoopbackAddress && !allowLoopback) ||
            address.isSiteLocalAddress ||
            address.isLinkLocalAddress ||
            address.isMulticastAddress ||
            address.isAnyLocalAddress ||
            address.hostAddress == "169.254.169.254" ||
            isCgnat(address) ||
            isUniqueLocalIpv6(address) ||
            isMappedForbiddenIpv4(address)

    private fun isCgnat(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 4 && bytes[0].toInt() and 0xff == 100 &&
            bytes[1].toInt() and 0xff in 64..127
    }

    private fun isUniqueLocalIpv6(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 16 && bytes[0].toInt() and 0xfe == 0xfc
    }

    private fun isMappedForbiddenIpv4(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != 16 || bytes.take(10).any { it.toInt() != 0 } ||
            (bytes[10].toInt() and 0xff) != 0xff || (bytes[11].toInt() and 0xff) != 0xff
        ) return false
        val mapped = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
        return isForbiddenAddress(mapped)
    }

    private fun configFailure(message: String, cause: Throwable? = null) =
        GenerationFailure(RemoteGenerationError(RemoteErrorKind.CONFIG, message), cause)
}

class ValidatingDns(
    private val policy: RemoteUrlPolicy,
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        policy.validateAddresses(hostname, addresses)
        return addresses
    }
}
