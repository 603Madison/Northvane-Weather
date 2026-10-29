package app.northvane.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.northvane.core.model.AlertCategory
import app.northvane.core.model.UnitSystem
import app.northvane.core.model.Activity
import app.northvane.core.radar.RadarSource
import app.northvane.core.radar.SatelliteImagery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** When to apply satellite limits: automatically on satellite/constrained networks, always, or never. */
enum class DataSaverMode { AUTO, ALWAYS, OFF }

/**
 * Everything that can be individually allowed or blocked while on a satellite
 * (or otherwise limited) connection. Sizes are typical compressed transfers.
 */
enum class SatelliteFeature(val title: String, val description: String, val approxSize: String, val defaultOn: Boolean) {
    ALERTS("Weather alerts", "NWS watches, warnings and advisories", "2–20 KB", true),
    BACKGROUND_ALERTS("Background alert checks", "Periodic checks that trigger notifications", "2–20 KB", true),
    CURRENT_CONDITIONS("Current conditions", "Nearest NWS observation station", "~4 KB", true),
    LOCAL_STATION("WeeWX station", "Your station, if it's reachable from the internet", "~1 KB", true),
    DAILY_FORECAST("Daily forecast", "7-day NWS forecast", "~4 KB", true),
    FORECAST_DETAILS("Forecast details", "Gusts, rain/snow/ice amounts, clouds, outage risk inputs", "~10 KB", true),
    HOURLY_FORECAST("Hourly forecast", "Next 48 hours", "~6 KB", true),
    HISTORY("Recent weather (NOAA)", "Past rain and temperatures for mud, foliage and seasonal outage estimates; at most every 6 hours", "~3 KB", true),
    POLLEN("Pollen forecast", "Google Pollen API, only if you've added a key; at most every 6 hours", "~6 KB", false),
    RADAR("Radar", "Past hour of radar images", "0.5–3 MB per view", false),
    FUTURE_CAST("Radar future cast", "HRRR model radar for the next hours", "0.5–2 MB per view", false),
    SATELLITE_IMAGERY("Satellite imagery", "GOES cloud imagery under the radar", "0.3–2 MB per view", false),
    OWM_FALLBACK("OpenWeatherMap fallback", "Only if enabled in Data sources", "~5 KB", false);

    companion object {
        val DEFAULTS: Set<SatelliteFeature> = entries.filter { it.defaultOn }.toSet()
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val units: UnitSystem = defaultUnits(),
    val selectedLocationId: String? = null,
    val alertsEnabled: Boolean = true,
    val alertCategories: Set<AlertCategory> = setOf(AlertCategory.WARNING, AlertCategory.WATCH, AlertCategory.ADVISORY),
    val alertCheckMinutes: Int = 30,
    val dataSaver: DataSaverMode = DataSaverMode.AUTO,
    val radarSource: RadarSource = RadarSource.NOAA_MRMS,
    val futureCastEnabled: Boolean = true,
    val futureCastMinutes: Int = 120,
    val nwsContact: String? = null,
    val owmEnabled: Boolean = false,
    val notificationPromptShown: Boolean = false,
    val satelliteFeatures: Set<SatelliteFeature> = SatelliteFeature.DEFAULTS,
    val outageRiskEnabled: Boolean = true,
    val pollenEnabled: Boolean = false,
    val activitiesEnabled: Boolean = true,
    val activities: Set<Activity> = Activity.entries.toSet(),
    val mudEnabled: Boolean = true,
    val foliageEnabled: Boolean = true,
    val satelliteLayer: Boolean = false,
    val satelliteImagery: SatelliteImagery = SatelliteImagery.AUTO,
) {
    companion object {
        private fun defaultUnits(): UnitSystem =
            if (Locale.getDefault().country in setOf("US", "LR", "MM")) UnitSystem.IMPERIAL else UnitSystem.METRIC
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val units = stringPreferencesKey("units")
        val selectedLocation = stringPreferencesKey("selected_location")
        val alertsEnabled = booleanPreferencesKey("alerts_enabled")
        val alertCategories = stringSetPreferencesKey("alert_categories")
        val alertInterval = intPreferencesKey("alert_interval")
        val dataSaver = stringPreferencesKey("data_saver")
        val radarSource = stringPreferencesKey("radar_source")
        val futureCast = booleanPreferencesKey("future_cast")
        val futureCastMinutes = intPreferencesKey("future_cast_minutes")
        val nwsContact = stringPreferencesKey("nws_contact")
        val owmEnabled = booleanPreferencesKey("owm_enabled")
        val notificationPrompt = booleanPreferencesKey("notification_prompt_shown")
        val satelliteFeatures = stringSetPreferencesKey("satellite_features")
        val outageRisk = booleanPreferencesKey("outage_risk")
        val pollen = booleanPreferencesKey("pollen")
        val activitiesEnabled = booleanPreferencesKey("activities_enabled")
        val activities = stringSetPreferencesKey("activities")
        val mud = booleanPreferencesKey("mud")
        val foliage = booleanPreferencesKey("foliage")
        val satelliteLayer = booleanPreferencesKey("satellite_layer")
        val satelliteImagery = stringPreferencesKey("satellite_imagery")
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                themeMode = p[Keys.theme].toEnum(d.themeMode),
                dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
                units = p[Keys.units].toEnum(d.units),
                selectedLocationId = p[Keys.selectedLocation],
                alertsEnabled = p[Keys.alertsEnabled] ?: d.alertsEnabled,
                alertCategories = p[Keys.alertCategories]
                    ?.mapNotNull { name -> AlertCategory.entries.firstOrNull { it.name == name } }
                    ?.toSet() ?: d.alertCategories,
                alertCheckMinutes = p[Keys.alertInterval] ?: d.alertCheckMinutes,
                dataSaver = p[Keys.dataSaver].toEnum(d.dataSaver),
                radarSource = p[Keys.radarSource].toEnum(d.radarSource),
                futureCastEnabled = p[Keys.futureCast] ?: d.futureCastEnabled,
                futureCastMinutes = p[Keys.futureCastMinutes] ?: d.futureCastMinutes,
                nwsContact = p[Keys.nwsContact],
                owmEnabled = p[Keys.owmEnabled] ?: d.owmEnabled,
                notificationPromptShown = p[Keys.notificationPrompt] ?: d.notificationPromptShown,
                satelliteFeatures = p[Keys.satelliteFeatures]
                    ?.mapNotNull { name -> SatelliteFeature.entries.firstOrNull { it.name == name } }
                    ?.toSet() ?: d.satelliteFeatures,
                outageRiskEnabled = p[Keys.outageRisk] ?: d.outageRiskEnabled,
                pollenEnabled = p[Keys.pollen] ?: d.pollenEnabled,
                activitiesEnabled = p[Keys.activitiesEnabled] ?: d.activitiesEnabled,
                activities = p[Keys.activities]
                    ?.mapNotNull { name -> Activity.entries.firstOrNull { it.name == name } }
                    ?.toSet() ?: d.activities,
                mudEnabled = p[Keys.mud] ?: d.mudEnabled,
                foliageEnabled = p[Keys.foliage] ?: d.foliageEnabled,
                satelliteLayer = p[Keys.satelliteLayer] ?: d.satelliteLayer,
                satelliteImagery = p[Keys.satelliteImagery].toEnum(d.satelliteImagery),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.dynamicColor] = enabled }
    suspend fun setUnits(units: UnitSystem) = store.edit { it[Keys.units] = units.name }
    suspend fun setSelectedLocation(id: String?) = store.edit {
        if (id == null) it.remove(Keys.selectedLocation) else it[Keys.selectedLocation] = id
    }
    suspend fun setAlertsEnabled(enabled: Boolean) = store.edit { it[Keys.alertsEnabled] = enabled }
    suspend fun setAlertCategory(category: AlertCategory, enabled: Boolean) = store.edit { p ->
        val current = p[Keys.alertCategories] ?: AppSettings().alertCategories.map { it.name }.toSet()
        p[Keys.alertCategories] = if (enabled) current + category.name else current - category.name
    }
    suspend fun setAlertInterval(minutes: Int) = store.edit { it[Keys.alertInterval] = minutes.coerceAtLeast(15) }
    suspend fun setDataSaver(mode: DataSaverMode) = store.edit { it[Keys.dataSaver] = mode.name }
    suspend fun setRadarSource(source: RadarSource) = store.edit { it[Keys.radarSource] = source.name }
    suspend fun setFutureCast(enabled: Boolean) = store.edit { it[Keys.futureCast] = enabled }
    suspend fun setFutureCastMinutes(minutes: Int) = store.edit { it[Keys.futureCastMinutes] = minutes.coerceIn(60, 360) }
    suspend fun setNwsContact(contact: String?) = store.edit {
        val clean = contact?.trim()?.take(100)
        if (clean.isNullOrEmpty()) it.remove(Keys.nwsContact) else it[Keys.nwsContact] = clean
    }
    suspend fun setOwmEnabled(enabled: Boolean) = store.edit { it[Keys.owmEnabled] = enabled }
    suspend fun setNotificationPromptShown() = store.edit { it[Keys.notificationPrompt] = true }
    suspend fun setSatelliteFeature(feature: SatelliteFeature, enabled: Boolean) = store.edit { p ->
        val current = p[Keys.satelliteFeatures] ?: SatelliteFeature.DEFAULTS.map { it.name }.toSet()
        p[Keys.satelliteFeatures] = if (enabled) current + feature.name else current - feature.name
    }
    suspend fun setOutageRisk(enabled: Boolean) = store.edit { it[Keys.outageRisk] = enabled }
    suspend fun setPollen(enabled: Boolean) = store.edit { it[Keys.pollen] = enabled }
    suspend fun setActivitiesEnabled(enabled: Boolean) = store.edit { it[Keys.activitiesEnabled] = enabled }
    suspend fun setActivity(activity: Activity, enabled: Boolean) = store.edit { p ->
        val current = p[Keys.activities] ?: Activity.entries.map { it.name }.toSet()
        p[Keys.activities] = if (enabled) current + activity.name else current - activity.name
    }
    suspend fun setMud(enabled: Boolean) = store.edit { it[Keys.mud] = enabled }
    suspend fun setFoliage(enabled: Boolean) = store.edit { it[Keys.foliage] = enabled }
    suspend fun setSatelliteLayer(enabled: Boolean) = store.edit { it[Keys.satelliteLayer] = enabled }
    suspend fun setSatelliteImagery(imagery: SatelliteImagery) = store.edit { it[Keys.satelliteImagery] = imagery.name }
}
