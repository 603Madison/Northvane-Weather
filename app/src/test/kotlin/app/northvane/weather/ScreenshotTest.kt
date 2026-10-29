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
import app.northvane.core.history.AcisClient
import app.northvane.core.air.EpaUvClient
import app.northvane.core.air.OpenMeteoAirClient
import app.northvane.core.model.Allergen
import app.northvane.core.model.Sensitivity
import app.northvane.core.model.UvForecast
import app.northvane.core.model.WeatherCondition
import app.northvane.core.traffic.LaneImpact
import app.northvane.core.traffic.TrafficClient
import app.northvane.core.traffic.TrafficEventType
import app.northvane.core.traffic.TrafficSnapshot
import app.northvane.weather.ui.settings.AllergiesScreen
import app.northvane.weather.ui.settings.ThemesScreen
import app.northvane.weather.ui.theme.AppTheme
import app.northvane.weather.ui.theme.ThemeWeather
import app.northvane.weather.ui.traffic.TrafficEventList
import app.northvane.weather.ui.traffic.TrafficUiState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.northvane.core.model.AlertCategory
import app.northvane.core.model.PollenForecast
import app.northvane.core.model.WeatherHistory
import app.northvane.core.outlook.Outlooks
import app.northvane.core.outlook.Periods
import app.northvane.core.pollen.PollenClient
import app.northvane.weather.ui.MainViewModel
import app.northvane.weather.ui.locations.LocationsScreen
import app.northvane.weather.ui.settings.DataSourcesScreen
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
            daily = Periods.fill(daily, emptyList(), grid, zone),
            alerts = emptyList(),
            forecastSource = DataSource.NWS,
            radarStation = point.radarStation,
            sunrise = sunrise,
            sunset = sunset,
            history = WeatherHistory(AcisClient.parseGrid(resource("me_acis.json")).first, elevationM = 20.0, fetchedAt = now),
            pollen = pollen(now.atZone(zone).toLocalDate()),
            airQuality = OpenMeteoAirClient.parse(resource("aq.json"), now).first,
            uv = uvToday(now, zone),
        ).let { Outlooks.annotate(it, location.latitude, location.longitude, now) }
    }

    /** Captured EPA hourly UV forecast, moved to today. */
    private fun uvToday(now: Instant, zone: java.time.ZoneId): UvForecast {
        val hours = EpaUvClient.parseHourly(resource("uv.json"), zone)
        val shift = java.time.Duration.between(hours.first().time.atZone(zone).toLocalDate().atStartOfDay(zone), now.atZone(zone).toLocalDate().atStartOfDay(zone))
        val moved = hours.map { it.copy(time = it.time.plus(shift)) }
        return UvForecast(moved, moved.maxOf { it.index }, source = "EPA UV Index", fetchedAt = now)
    }

    /** Captured Google Pollen API response, moved to today's dates. */
    private fun pollen(today: java.time.LocalDate): PollenForecast {
        val days = PollenClient.parse(resource("pollen.json"))
        return PollenForecast(days.mapIndexed { i, d -> d.copy(date = today.plusDays(i.toLong())) }, Instant.now())
    }

    /** Demo only: turns the third day into a late-October nor'easter to show the outage outlook. */
    private fun withNorEaster(s: WeatherSnapshot): WeatherSnapshot {
        val zone = java.time.ZoneId.of(s.timeZoneId)
        val days = s.daily.toMutableList()
        days[2] = days[2].copy(
            gustMaxKph = Units.mphToKph(55.0), windMaxKph = Units.mphToKph(30.0), windDirectionDeg = 45.0,
            precipitationMm = Units.inchesToMm(1.8), precipitationChance = 100, thunderChance = 0,
        )
        return Outlooks.annotate(s.copy(daily = days.map { it.copy(daytime = null, overnight = null) }), location.latitude, location.longitude)
    }

    private fun state(
        snapshot: WeatherSnapshot? = snapshot(),
        settings: AppSettings = AppSettings(
            units = UnitSystem.IMPERIAL,
            pollenEnabled = true,
            allergies = mapOf(Allergen.BIRCH to Sensitivity.SEVERE, Allergen.GRASS to Sensitivity.MILD, Allergen.MOLD to Sensitivity.MODERATE),
        ),
        network: NetworkStatus = NetworkStatus(),
        liteMode: Boolean = false,
    ) = MainUiState(
        ready = true,
        locations = listOf(location, SavedLocation("nh", "Concord, NH", 43.2081, -71.5376, nickname = "Mom's", notify = false)),
        selected = location,
        snapshot = snapshot,
        settings = settings.copy(notificationPromptShown = true),
        network = network,
        liteMode = liteMode,
        pollenKeySet = settings.pollenEnabled,
    )

    private val noActions = HomeActions({}, {}, {}, {}, {}, {})

    private fun render(dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            NorthvaneTheme(darkTheme = dark, dynamicColor = false) {
                Box {
                    Backdrop()
                    content()
                }
            }
        }
        compose.waitForIdle()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(qualifiers = "w400dp-h4600dp-xxhdpi")
    fun todayFullLight() {
        render(dark = false) { HomeScreen(state(), noActions, {}, {}, {}) }
        save("today_full_light")
        compose.onAllNodesWithText("Hiking")[0].performClick()
        save("today_full_light_hiking_open")
    }

    @Test
    @Config(qualifiers = "w400dp-h1400dp-xxhdpi")
    fun warningTurnsTodayRed() {
        val base = snapshot()
        val warning = NwsParsers.parseAlerts(resource("nws_alerts_tx.json")).first().copy(
            event = "High Wind Warning", category = AlertCategory.WARNING,
            ends = Instant.now().plusSeconds(6 * 3600), expires = Instant.now().plusSeconds(6 * 3600),
        )
        render(dark = false) { HomeScreen(state(base.copy(alerts = listOf(warning))), noActions, {}, {}, {}) }
        save("today_warning_light")
    }

    @Test
    @Config(qualifiers = "w400dp-h1400dp-xxhdpi")
    fun warningTurnsTodayRedDark() {
        val base = snapshot()
        val warning = NwsParsers.parseAlerts(resource("nws_alerts_tx.json")).first().copy(
            event = "Winter Storm Warning", category = AlertCategory.WARNING,
            ends = Instant.now().plusSeconds(6 * 3600), expires = Instant.now().plusSeconds(6 * 3600),
        )
        render(dark = true) { HomeScreen(state(base.copy(alerts = listOf(warning))), noActions, {}, {}, {}) }
        save("today_warning_dark")
    }

    @Test
    fun watchStaysNormal() {
        val base = snapshot()
        val watch = NwsParsers.parseAlerts(resource("nws_alerts_tx.json")).first().copy(
            event = "Winter Storm Watch", category = AlertCategory.WATCH,
            ends = Instant.now().plusSeconds(6 * 3600), expires = Instant.now().plusSeconds(6 * 3600),
        )
        render(dark = false) { HomeScreen(state(base.copy(alerts = listOf(watch))), noActions, {}, {}, {}) }
        save("today_watch_light")
    }

    @Test
    @Config(qualifiers = "w400dp-h1600dp-xxhdpi")
    fun themesGallery() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        var current by androidx.compose.runtime.mutableStateOf(AppTheme.HARBOR to false)
        var showPicker by androidx.compose.runtime.mutableStateOf(false)
        val st = state()
        compose.setContent {
            val (theme, dark) = current
            NorthvaneTheme(darkTheme = dark, dynamicColor = false, theme = theme, weather = ThemeWeather(WeatherCondition.CLEAR, true, 12.0)) {
                Box {
                    Backdrop()
                    if (showPicker) ThemesScreen(st.copy(settings = st.settings.copy(appTheme = theme)), container, onBack = {}) else HomeScreen(st, noActions, {}, {}, {})
                }
            }
        }
        for (theme in listOf(AppTheme.HARBOR, AppTheme.MAPLE, AppTheme.LUPINE, AppTheme.AURORA, AppTheme.LIVE_SKY, AppTheme.TEMPERATURE)) {
            for (dark in listOf(false, true)) {
                current = theme to dark
                save("theme_${theme.name.lowercase()}_${if (dark) "dark" else "light"}")
            }
        }
        current = AppTheme.EVERGREEN to false
        showPicker = true
        save("themes_screen")
    }

    @Composable
    private fun Backdrop() {
        val b = app.northvane.weather.ui.theme.LocalBackdrop.current
        Box(Modifier.fillMaxSize().background(b.brush))
    }

    @Test
    @Config(qualifiers = "w400dp-h2600dp-xxhdpi")
    fun allergiesEditor() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        render(dark = false) { AllergiesScreen(state(), container, onBack = {}) }
        save("allergies_editor")
    }

    @Test
    @Config(qualifiers = "w400dp-h1800dp-xxhdpi")
    fun trafficList() {
        val events = TrafficClient.parseWzdx(resource("wzdx.json"), location.point, 40.0, Instant.parse("2026-10-29T18:00:00Z"), "NHDOT/VTAOT/MEDOT")
        val crash = events.first().copy(
            id = "crash", type = TrafficEventType.CRASH, road = "I-295", direction = "northbound",
            description = "Crash blocking the right lane near Exit 7.", impact = LaneImpact.SOME_CLOSED,
            start = Instant.now().minusSeconds(1200), end = null, distanceKm = 1.4, source = "New England 511",
        )
        val snapshot = TrafficSnapshot(
            listOf(crash) + events.map { it.copy(end = it.end?.let { e -> maxOf(e, Instant.now().plusSeconds(86400)) }) },
            listOf("NHDOT/VTAOT/MEDOT (WZDx)", "New England 511"), Instant.now(),
        )
        val zone = java.time.ZoneId.of("America/New_York")
        render(dark = false) {
            TrafficEventList(
                TrafficUiState(snapshot = snapshot, hasNe511Key = true), UnitSystem.IMPERIAL, "ME",
                app.northvane.weather.ui.TimeFormat(zone, false), onFocus = {},
            )
        }
        save("traffic_list")
    }

    @Test
    fun locationsWithNicknames() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        val vm = MainViewModel(container)
        render(dark = false) { LocationsScreen(state(), vm, container, onBack = {}) }
        compose.onAllNodesWithContentDescription("Options for Mom's")[0].performClick()
        save("locations_menu")
    }

    @Test
    @Config(qualifiers = "w400dp-h3200dp-xxhdpi")
    fun dataSourcesPollen() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        render(dark = false) { DataSourcesScreen(state(), container, onBack = {}, onSourcesChanged = {}) }
        save("data_sources")
    }

    @Test
    @Config(qualifiers = "w400dp-h3400dp-xxhdpi")
    fun noreasterOutageDark() {
        val storm = withNorEaster(snapshot())
        render(dark = true) { HomeScreen(state(storm), noActions, {}, {}, {}) }
        val name = java.time.format.TextStyle.SHORT.let { storm.daily[2].date.dayOfWeek.getDisplayName(it, java.util.Locale.getDefault()) }
        compose.onAllNodesWithText(name)[0].performClick()
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
    @Config(qualifiers = "w400dp-h4200dp-xxhdpi")
    fun settingsLight() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        render(dark = false) { SettingsScreen(state(), container, onBack = {}, onOpenSources = {}) }
        save("settings_light")
    }
}
