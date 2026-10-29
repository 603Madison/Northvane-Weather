package app.northvane.core.weewx

import kotlinx.serialization.Serializable

@Serializable
enum class WeeWxFormat(val label: String) {
    AUTO("Auto-detect"),
    NORTHVANE_JSON("Northvane JSON template"),
    CUMULUS_REALTIME("realtime.txt (weewx-crt)"),
    CLIENTRAW("clientraw.txt"),
}

@Serializable
enum class WeeWxAuth(val label: String) {
    NONE("None"),
    BASIC("Username & password"),
    BEARER("Bearer token"),
    HEADER("Custom header"),
}

/**
 * A user's WeeWX station. Non-secret settings only; passwords and tokens live
 * in the encrypted secret store and are passed in as [WeeWxSecret].
 */
@Serializable
data class WeeWxStation(
    val id: String,
    val name: String,
    val url: String,
    val format: WeeWxFormat = WeeWxFormat.AUTO,
    val auth: WeeWxAuth = WeeWxAuth.NONE,
    val username: String? = null,
    val headerName: String? = null,
    /** Saved location whose current conditions this station overrides. */
    val linkedLocationId: String? = null,
    /** Data older than this is treated as unavailable and NWS is used instead. */
    val maxAgeMinutes: Int = 30,
    val enabled: Boolean = true,
)

/** Secret material for a station: the password, bearer token, or custom header value. */
data class WeeWxSecret(val value: String?) {
    override fun toString(): String = "WeeWxSecret(***)"
}
