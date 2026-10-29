package app.northvane.core.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Rules for URLs the *user* types in (their WeeWX server). Built-in services
 * are all fixed HTTPS endpoints and never pass through here.
 *
 * - Only http/https.
 * - No credentials embedded in the URL (they would end up in logs/history);
 *   credentials go in dedicated, encrypted fields instead.
 * - Plain HTTP only for hosts on a private network (RFC 1918, loopback,
 *   link-local, CGNAT/Tailscale, IPv6 ULA, or a hostname that resolves to one
 *   of those — enforced at connect time by [PrivateNetworkDns]).
 * - Credentials are never sent over plain HTTP, even on a LAN.
 */
object UrlPolicy {

    sealed interface Validation {
        data class Valid(val url: HttpUrl, val isCleartext: Boolean) : Validation
        data class Invalid(val reason: String) : Validation
    }

    fun validateUserServerUrl(raw: String, sendsCredentials: Boolean): Validation {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Validation.Invalid("Enter a URL")
        if (trimmed.length > 2048) return Validation.Invalid("URL is too long")
        if (!trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return Validation.Invalid("URL must start with https:// or http://")
        }
        val url = trimmed.toHttpUrlOrNull() ?: return Validation.Invalid("Not a valid URL")
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return Validation.Invalid("Don't put a username or password in the URL; use the login fields instead")
        }
        if (url.isHttps) return Validation.Valid(url, isCleartext = false)

        if (sendsCredentials) {
            return Validation.Invalid("Credentials are only sent over HTTPS. Use an https:// URL or remove the login.")
        }
        if (isIpLiteral(url.host) && !isPrivateIpLiteral(url.host)) {
            return Validation.Invalid("Plain HTTP is only allowed for servers on your local network. Use HTTPS for public addresses.")
        }
        return Validation.Valid(url, isCleartext = true)
    }

    /**
     * Whether a host name *looks* local without resolving it: a private (non-loopback)
     * address literal, or an mDNS/home-router style name. Hostnames on public
     * domains may still resolve to LAN addresses (split DNS); callers that can
     * do a lookup should also check the resolved addresses with [isLocalNetworkAddress].
     */
    fun looksLikeLocalNetworkHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (isIpLiteral(h)) {
            val address = runCatching { InetAddress.getByName(h) }.getOrNull() ?: return false
            return isLocalNetworkAddress(address)
        }
        return '.' !in h || LOCAL_SUFFIXES.any { h.endsWith(it) }
    }

    /** Private-network addresses other than this device's own loopback. */
    fun isLocalNetworkAddress(address: InetAddress): Boolean =
        !address.isLoopbackAddress && isPrivateAddress(address)

    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal", ".localdomain", ".home")

    fun isIpLiteral(host: String): Boolean =
        IPV4.matches(host) || (host.contains(':') && host.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '%' })

    fun isPrivateIpLiteral(host: String): Boolean {
        if (!isIpLiteral(host)) return false
        // For literals InetAddress only parses; it performs no DNS lookup.
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return isPrivateAddress(address)
    }

    fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
        val b = address.address
        return when (address) {
            // 100.64.0.0/10 carrier-grade NAT, used by Tailscale and similar overlays.
            is Inet4Address -> (b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 64
            // fc00::/7 unique local addresses.
            is Inet6Address -> (b[0].toInt() and 0xFE) == 0xFC
            else -> false
        }
    }

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
}
