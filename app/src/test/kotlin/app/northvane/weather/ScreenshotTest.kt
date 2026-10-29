package app.northvane.weather

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.northvane.core.model.DataSource
import app.northvane.core.model.SavedLocation
import app.northvane.core.model.Section
import app.northvane.core.model.UnitSystem
import app.northvane.core.model.Units
import app.northvane.core.model.WeatherSnapshot
import app.northvane.core.nws.ForecastDetails
import app.northvane.core.nws.NwsClient
import app.northvane.core.nws.NwsGridData
import app.northvane.core.nws.NwsParsers
import app.northvane.core.outage.OutageEstimator
import app.northvane.core.util.Solar
import app.northvane.weather.data.AppSettings
import app.northvane.weather.data.DataSaverMode
import app.northvane.weather.data.SatelliteFeature
import app.northvane.weather.net.NetworkStatus
import app.northvane.weather.ui.MainUiState
import app.northvane.weather.ui.alerts.AlertsScreen
import app.northvane.weather.ui.home.HomeActions
import app.northvane.weather.ui.home.HomeScreen
import app.northvane.weather.ui.radar.RadarScreen
import app.northvane.weather.ui.settings.SettingsScreen
import app.northvane.weather.ui.theme.NorthvaneTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * Renders real screens with real NWS data for Portland, ME (captured
 * responses) to PNGs in app/build/outputs/screenshots, for visual review.
 * Not a pixel-diff test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun resource(name: String) = requireNotNull(javaClass.getResource("/live/$name")).readText()

    private val location = SavedLocation("home", "Portland, ME", 43.6591, -70.2568)

    private fun snapshot(): WeatherSnapshot {
        val point = NwsParsers.parsePoint(resource("me_point.json"))
        val zone = NwsClient.zoneOf(point)
        val grid = NwsGridData.parse(resource("me_grid.json"))
        val now = Instant.now()
        val (sunrise, sunset) = Solar.sunTimes(location.latitude, location.longitude, now.atZone(zone).toLocalDate(), zone)
        val daily = ForecastDetails.enrichDaily(NwsParsers.parseDaily(resource("me_daily.json"), zone), grid, zone)
        return WeatherSnapshot(
            locationId = location.id,
            fetchedAt = now,
            timeZoneId = point.timeZone,
            placeName = point.placeName,
            current = NwsParsers.parseObservation(resource("me_obs.json"), null),
            hourly = ForecastDetails.enrichHourly(NwsParsers.parseHourly(resource("me_hourly.json")), grid),
            daily = OutageEstimator.annotate(daily, emptyList(), zone),
            alerts = emptyList(),
            forecastSource = DataSource.NWS,
            radarStation = point.radarStation,
            sunrise = sunrise,
            sunset = sunset,
        )
    }

    /** Demo only: turns the third day into a late-October nor'easter to show the outage outlook. */
    private fun withNorEaster(s: WeatherSnapshot): WeatherSnapshot {
        val zone = java.time.ZoneId.of(s.timeZoneId)
        val days = s.daily.toMutableList()
        days[2] = days[2].copy(
            gustMaxKph = Units.mphToKph(55.0), windMaxKph = Units.mphToKph(30.0), windDirectionDeg = 45.0,
            precipitationMm = Units.inchesToMm(1.8), precipitationChance = 100, thunderChance = 0,
        )
        return s.copy(daily = OutageEstimator.annotate(days, s.alerts, zone))
    }

    private fun state(
        snapshot: WeatherSnapshot? = snapshot(),
        settings: AppSettings = AppSettings(units = UnitSystem.IMPERIAL),
        network: NetworkStatus = NetworkStatus(),
        liteMode: Boolean = false,
    ) = MainUiState(
        ready = true,
        locations = listOf(location, SavedLocation("nh", "Concord, NH", 43.2081, -71.5376)),
        selected = location,
        snapshot = snapshot,
        settings = settings.copy(notificationPromptShown = true),
        network = network,
        liteMode = liteMode,
    )

    private val noActions = HomeActions({}, {}, {}, {}, {}, {})

    private fun render(dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { NorthvaneTheme(darkTheme = dark, dynamicColor = false, content = content) }
        compose.waitForIdle()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(qualifiers = "w400dp-h2400dp-xxhdpi")
    fun todayFullLight() {
        render(dark = false) { HomeScreen(state(), noActions, {}, {}, {}) }
        save("today_full_light")
        compose.onAllNodesWithText("Wind")[0].performClick()
        save("today_full_light_wind")
    }

    @Test
    @Config(qualifiers = "w400dp-h2400dp-xxhdpi")
    fun noreasterOutageDark() {
        val storm = withNorEaster(snapshot())
        render(dark = true) { HomeScreen(state(storm), noActions, {}, {}, {}) }
        val name = java.time.format.TextStyle.SHORT.let { storm.daily[2].date.dayOfWeek.getDisplayName(it, java.util.Locale.getDefault()) }
        compose.onNodeWithText(name).performClick()
        save("today_noreaster_dark")
    }

    @Test
    fun todayDarkWithLocalStation() {
        val base = snapshot()
        val local = base.current!!.copy(
            source = DataSource.WEEWX, stationName = "Backyard", temperatureC = 11.4, feelsLikeC = 10.2,
            humidityPercent = 71.0, windSpeedKph = 9.0, windGustKph = 24.0, windDirectionDeg = 290.0,
            observedAt = Instant.now().minusSeconds(90), supplementedFrom = DataSource.NWS,
        )
        render(dark = true) { HomeScreen(state(base.copy(current = local)), noActions, {}, {}, {}) }
        save("today_dark_weewx")
    }

    @Test
    fun todaySatelliteMetric() {
        val settings = AppSettings(
            units = UnitSystem.METRIC,
            dataSaver = DataSaverMode.AUTO,
            satelliteFeatures = SatelliteFeature.DEFAULTS - SatelliteFeature.HOURLY_FORECAST,
        )
        val lite = snapshot().copy(hourly = emptyList(), liteMode = true, skippedSections = setOf(Section.HOURLY_FORECAST))
        render(dark = false) {
            HomeScreen(state(lite, settings, NetworkStatus(satellite = true, constrained = true), liteMode = true), noActions, {}, {}, {})
        }
        save("today_satellite_metric")
    }

    @Test
    fun onboarding() {
        render(dark = false) { HomeScreen(MainUiState(ready = true), noActions, {}, {}, {}) }
        save("onboarding")
    }

    @Test
    fun alertsDark() {
        val withAlerts = snapshot().copy(alerts = NwsParsers.parseAlerts(resource("nws_alerts_tx.json")))
        render(dark = true) { AlertsScreen(state(withAlerts), onRefresh = {}, onOpenSettings = {}) }
        save("alerts_dark")
    }

    @Test
    fun radarPausedOnSatellite() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        render(dark = false) {
            RadarScreen(state(liteMode = true, network = NetworkStatus(satellite = true)), container, onOpenSettings = {})
        }
        save("radar_satellite")
    }

    @Test
    @Config(qualifiers = "w400dp-h3000dp-xxhdpi")
    fun settingsLight() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        render(dark = false) { SettingsScreen(state(), container, onBack = {}, onOpenSources = {}) }
        save("settings_light")
    }
}
