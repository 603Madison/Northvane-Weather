package app.northvane.core.net

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Request tag marking a request as a user-configured local-network server
 * that may use plain HTTP. Requests without it are HTTPS-only.
 */
object LanCleartextAllowed

/**
 * Last line of defence against accidental cleartext traffic: any http:// request
 * must be explicitly tagged [LanCleartextAllowed] and target a private-network
 * host. This runs in-app in addition to the Android network security config.
 */
class CleartextGuardInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isHttps) {
            val allowed = request.tag(LanCleartextAllowed::class.java) != null
            if (!allowed) throw IOException("Blocked insecure (http) request")
            if (UrlPolicy.isIpLiteral(request.url.host) && !UrlPolicy.isPrivateIpLiteral(request.url.host)) {
                throw IOException("Blocked insecure request to a public address")
            }
            if (request.header("Authorization") != null) {
                throw IOException("Blocked credentials over insecure connection")
            }
        }
        return chain.proceed(request)
    }
}

/**
 * DNS used for cleartext local-network requests: only returns private
 * addresses, so a hostname that (re)resolves to the public internet — for
 * example via DNS rebinding or a typo — can't receive unencrypted traffic.
 */
class PrivateNetworkDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val privateOnly = delegate.lookup(hostname).filter(UrlPolicy::isPrivateAddress)
        if (privateOnly.isEmpty()) {
            throw UnknownHostException(
                "$hostname is not on a private network; plain HTTP is only allowed for local servers",
            )
        }
        return privateOnly
    }
}

class DefaultHeadersInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder: Request.Builder = request.newBuilder()
        if (request.header("User-Agent") == null) builder.header("User-Agent", userAgent)
        return chain.proceed(builder.build())
    }
}
