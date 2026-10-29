package app.northvane.core.net

import java.io.IOException

/** Base type for failures the UI can explain to the user. */
open class WeatherApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

class HttpStatusException(val code: Int, val service: String) :
    WeatherApiException("$service responded with HTTP $code")

/** api.weather.gov only covers the US and its territories. */
class OutsideNwsCoverageException :
    WeatherApiException("This location is outside National Weather Service coverage")

class InvalidCredentialsException(service: String) :
    WeatherApiException("$service rejected the configured credentials")

class RateLimitedException(service: String) :
    WeatherApiException("$service rate limit reached; try again later")

class ResponseTooLargeException(limitBytes: Long) :
    WeatherApiException("Response exceeded ${limitBytes / 1024} KB limit")

class StaleDataException(message: String) : WeatherApiException(message)

class InsecureConfigurationException(message: String) : WeatherApiException(message)

class ParseException(message: String, cause: Throwable? = null) : WeatherApiException(message, cause)

/** Converts any failure into a short, user-facing sentence. Never includes URLs or secrets. */
fun Throwable.toUserMessage(): String = when (this) {
    is WeatherApiException -> message ?: "Request failed"
    is java.net.UnknownHostException -> "Couldn't reach the server (no connection or unknown host)"
    is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "The request timed out"
    is javax.net.ssl.SSLException -> "Secure connection failed (certificate or TLS problem)"
    is java.net.ConnectException -> "Connection refused"
    is IOException -> "Network error"
    else -> "Unexpected error"
}
