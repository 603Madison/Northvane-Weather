package app.northvane.core

import app.northvane.core.model.DataSource
import app.northvane.core.model.SavedLocation
import app.northvane.core.model.WeatherCondition
import app.northvane.core.net.HttpClients
import app.northvane.core.nws.NwsClient
import app.northvane.core.owm.OwmClient
import app.northvane.core.repo.CachedNwsPoint
import app.northvane.core.model.DetailSource
import app.northvane.core.model.OutageLevel
import app.northvane.core.model.Section
import app.northvane.core.repo.FetchPlan
import app.northvane.core.repo.NwsPointCache
import app.northvane.core.repo.SourceConfig
import app.northvane.core.repo.WeatherRepository
import app.northvane.core.repo.WeatherUnavailableException
import app.northvane.core.weewx.WeeWxAuth
import app.northvane.core.weewx.WeeWxClient
import app.northvane.core.weewx.WeeWxSecret
import app.northvane.core.weewx.WeeWxStation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherRepositoryTest {

    private val kansas = SavedLocation("home", "Home", 39.74561, -97.08923)
    private val london = SavedLocation("ldn", "London", 51.5085, -0.1257)

    private class MemoryCache : NwsPointCache {
        val map = mutableMapOf<String, CachedNwsPoint>()
        override suspend fun get(key: String) = map[key]
        override suspend fun put(key: String, value: CachedNwsPoint) { map[key] = value }
    }

    private fun FakeNetwork.nwsHappyPath() {
        on("/points/39.7456,-97.0892", body = Nws.point)
        on("/gridpoints/TOP/32,81/stations", body = Nws.stations)
        on("/stations/KMYZ/observations/latest", body = Nws.observation())
        on("/gridpoints/TOP/32,81/forecast/hourly", body = Nws.hourly)
        on("/gridpoints/TOP/32,81/forecast", body = Nws.daily)
        on("/alerts/active?point=39.7456,-97.0892", body = Nws.alerts)
        on("/gridpoints/TOP/32,81", body = Nws.grid)
    }

    private fun repo(fake: FakeNetwork, cache: NwsPointCache = MemoryCache()): WeatherRepository {
        val base = HttpClients.create("test-agent", cacheDir = null).newBuilder().addInterceptor(fake).build()
        return WeatherRepository(
            nws = NwsClient(base, { "Northvane/test (tests)" }),
            owm = OwmClient(base),
            weewx = WeeWxClient(HttpClients.localServerClients(base)),
            pointCache = cache,
            clock = { NOW },
        )
    }

    @Test
    fun nwsOnly() = runTest {
        val fake = FakeNetwork().apply { nwsHappyPath() }
        val snapshot = repo(fake).fetch(kansas, SourceConfig(), FetchPlan.FULL)

        val current = assertNotNull(snapshot.current)
        assertEquals(DataSource.NWS, current.source)
        assertEquals(12.2, current.temperatureC!!, 1e-9)
        assertEquals("Linn, KS", snapshot.placeName)
        assertEquals("America/Chicago", snapshot.timeZoneId)
        assertEquals(DataSource.NWS, snapshot.forecastSource)
        assertEquals(3, snapshot.hourly.size)
        assertEquals(2, snapshot.daily.size)
        assertEquals(3, snapshot.alerts.size)
        assertTrue(snapshot.issues.isEmpty(), "issues: ${snapshot.issues}")
        assertNotNull(snapshot.sunrise)
        assertTrue(fake.requests.all { it.url.host == "api.weather.gov" })
        assertTrue(fake.requests.all { it.header("User-Agent") == "Northvane/test (tests)" })
        assertTrue(fake.requests.none { it.url.host.contains("openweathermap") })
    }

    @Test
    fun pointMetadataIsCached() = runTest {
        val cache = MemoryCache()
        val fake = FakeNetwork().apply { nwsHappyPath() }
        val repository = repo(fake, cache)
        repository.fetch(kansas, SourceConfig(), FetchPlan.FULL)
        fake.requests.clear()
        repository.fetch(kansas, SourceConfig(), FetchPlan.FULL)
        assertTrue(fake.requests.none { it.url.encodedPath.startsWith("/points/") })
        assertTrue(fake.requests.none { it.url.encodedPath.endsWith("/stations") })
    }

    @Test
    fun liteModeSkipsHourly() = runTest {
        val fake = FakeNetwork().apply { nwsHappyPath() }
        val plan = FetchPlan(
            sections = setOf(Section.CURRENT_CONDITIONS, Section.ALERTS, Section.DAILY_FORECAST),
            observationStations = 1,
            limited = true,
        )
        val snapshot = repo(fake).fetch(kansas, SourceConfig(), plan)
        assertTrue(snapshot.liteMode)
        assertTrue(snapshot.hourly.isEmpty())
        assertEquals(2, snapshot.daily.size)
        assertTrue(fake.requests.none { it.url.encodedPath.endsWith("/forecast/hourly") })
        assertTrue(fake.requests.none { it.url.encodedPath == "/gridpoints/TOP/32,81" })
        assertEquals(setOf(Section.HOURLY_FORECAST, Section.FORECAST_DETAILS, Section.LOCAL_STATION, Section.OWM_FALLBACK), snapshot.skippedSections)
        // Without grid data, numbers come from the forecast text (the fixture states winds).
        assertEquals(DetailSource.TEXT, snapshot.daily.first().detailSource)
    }

    @Test
    fun everySectionOffStillSucceeds() = runTest {
        val fake = FakeNetwork().apply { nwsHappyPath() }
        val snapshot = repo(fake).fetch(kansas, SourceConfig(), FetchPlan(sections = emptySet(), limited = true))
        assertTrue(fake.requests.isEmpty(), "nothing downloaded")
        assertEquals(Section.entries.toSet(), snapshot.skippedSections)
    }

    @Test
    fun gridDataEnrichesForecastAndDrivesOutageRisk() = runTest {
        val fake = FakeNetwork().apply {
            nwsHappyPath()
        }
        val snapshot = repo(fake).fetch(kansas, SourceConfig(), FetchPlan.FULL)
        val today = snapshot.daily.first()
        assertEquals(DetailSource.GRID, today.detailSource)
        assertEquals(96.0, today.gustMaxKph!!, 1e-9)
        assertEquals(12.0, today.precipitationMm!!, 1e-9)
        assertEquals(OutageLevel.VERY_HIGH, today.outageRisk!!.level, "60 mph gusts with leaves on")
        val hour = snapshot.hourly.first()
        assertEquals(96.0, hour.windGustKph!!, 1e-9)
        assertEquals(2.0, hour.precipitationMm!!, 1e-9) // 12 mm over 6 h
        assertEquals(90, hour.skyCoverPercent)
    }

    @Test
    fun weewxOverridesCurrentConditions() = runTest {
        val fake = FakeNetwork().apply {
            nwsHappyPath()
            on("https://wx.example.com/northvane.json", body = WeeWx.northvaneJson)
        }
        val station = WeeWxStation(
            id = "s1", name = "Backyard", url = "https://wx.example.com/northvane.json",
            auth = WeeWxAuth.BASIC, username = "me", linkedLocationId = "home",
        )
        val snapshot = repo(fake).fetch(kansas, SourceConfig(station, WeeWxSecret("hunter2")), FetchPlan.FULL)

        val current = assertNotNull(snapshot.current)
        assertEquals(DataSource.WEEWX, current.source)
        assertEquals(10.5, current.temperatureC!!, 1e-9)
        assertEquals("Partly Cloudy", current.description, "sky condition filled from NWS")
        assertEquals(WeatherCondition.PARTLY_CLOUDY, current.condition)
        assertEquals(16.09, current.visibilityKm!!, 1e-6, "visibility filled from NWS")
        assertEquals(DataSource.NWS, current.supplementedFrom)
        val weewxRequest = fake.requests.single { it.url.host == "wx.example.com" }
        assertEquals("Basic bWU6aHVudGVyMg==", weewxRequest.header("Authorization"))
    }

    @Test
    fun weewxForOtherLocationIsIgnored() = runTest {
        val fake = FakeNetwork().apply { nwsHappyPath() }
        val station = WeeWxStation(id = "s1", name = "Cabin", url = "https://wx.example.com/x.json", linkedLocationId = "cabin")
        val snapshot = repo(fake).fetch(kansas, SourceConfig(station), FetchPlan.FULL)
        assertEquals(DataSource.NWS, snapshot.current!!.source)
        assertTrue(fake.requests.none { it.url.host == "wx.example.com" })
    }

    @Test
    fun staleWeewxFallsBackToNws() = runTest {
        val old = WeeWx.northvaneJson.replace("\"time\": ${NOW.epochSecond - 120}", "\"time\": ${NOW.epochSecond - 7200}")
        val fake = FakeNetwork().apply {
            nwsHappyPath()
            on("https://wx.example.com/northvane.json", body = old)
        }
        val station = WeeWxStation(id = "s1", name = "Backyard", url = "https://wx.example.com/northvane.json", linkedLocationId = "home")
        val snapshot = repo(fake).fetch(kansas, SourceConfig(station), FetchPlan.FULL)
        assertEquals(DataSource.NWS, snapshot.current!!.source)
        assertTrue(snapshot.issues.any { it.source == DataSource.WEEWX && "minutes old" in it.message })
    }

    @Test
    fun cleartextWeewxWithPasswordIsRefused() = runTest {
        val fake = FakeNetwork().apply {
            nwsHappyPath()
            on("http://192.168.1.50/northvane.json", body = WeeWx.northvaneJson)
        }
        val station = WeeWxStation(
            id = "s1", name = "Backyard", url = "http://192.168.1.50/northvane.json",
            auth = WeeWxAuth.BASIC, username = "me", linkedLocationId = "home",
        )
        val snapshot = repo(fake).fetch(kansas, SourceConfig(station, WeeWxSecret("pw")), FetchPlan.FULL)
        assertEquals(DataSource.NWS, snapshot.current!!.source)
        assertTrue(fake.requests.none { it.url.host == "192.168.1.50" }, "nothing was sent")
        assertTrue(snapshot.issues.any { it.source == DataSource.WEEWX && "HTTPS" in it.message })
    }

    @Test
    fun cleartextWeewxOnLanWithoutAuthWorks() = runTest {
        val fake = FakeNetwork().apply {
            nwsHappyPath()
            on("http://192.168.1.50/northvane.json", body = WeeWx.northvaneJson)
        }
        val station = WeeWxStation(id = "s1", name = "Backyard", url = "http://192.168.1.50/northvane.json", linkedLocationId = "home")
        val snapshot = repo(fake).fetch(kansas, SourceConfig(station), FetchPlan.FULL)
        assertEquals(DataSource.WEEWX, snapshot.current!!.source)
    }

    @Test
    fun blockedLocalStationIsSkippedWithReason() = runTest {
        val fake = FakeNetwork().apply {
            nwsHappyPath()
            on("http://192.168.1.50/northvane.json", body = WeeWx.northvaneJson)
        }
        val station = WeeWxStation(id = "s1", name = "Backyard", url = "http://192.168.1.50/northvane.json", linkedLocationId = "home")
        val config = SourceConfig(station, weewxBlockedReason = "Allow Nearby devices")
        val snapshot = repo(fake).fetch(kansas, config, FetchPlan.FULL)
        assertEquals(DataSource.NWS, snapshot.current!!.source)
        assertTrue(fake.requests.none { it.url.host == "192.168.1.50" })
        assertTrue(snapshot.issues.any { it.source == DataSource.WEEWX && it.message == "Allow Nearby devices" })
    }

    @Test
    fun staleNwsObservationUsesNextStation() = runTest {
        val fake = FakeNetwork().apply {
            nwsHappyPath()
            on("/stations/KMYZ/observations/latest", body = Nws.observation(tempC = "null"))
            on("/stations/KCNK/observations/latest", body = Nws.observation(tempC = "8.0"))
        }
        val snapshot = repo(fake).fetch(kansas, SourceConfig(), FetchPlan.FULL)
        assertEquals(8.0, snapshot.current!!.temperatureC!!, 1e-9)
    }

    @Test
    fun observationsDownFallsBackToForecastEstimate() = runTest {
        val fake = FakeNetwork().apply {
            nwsHappyPath()
            on("/stations/KMYZ/observations/latest", code = 503, body = "{}")
        }
        val snapshot = repo(fake).fetch(kansas, SourceConfig(), FetchPlan.FULL)
        val current = snapshot.current!!
        assertEquals(DataSource.NWS_FORECAST_ESTIMATE, current.source)
        assertEquals(12.22, current.temperatureC!!, 0.01)
    }

    @Test
    fun owmFillsGapsOnlyWhenConfigured() = runTest {
        val fake = FakeNetwork().apply {
            nwsHappyPath()
            on("/stations/KMYZ/observations/latest", code = 503, body = "{}")
            on("/gridpoints/TOP/32,81/forecast", code = 500, body = "{}")
            on("/gridpoints/TOP/32,81/forecast/hourly", code = 500, body = "{}")
            on("api.openweathermap.org/data/2.5/weather", body = Owm.current)
            on("api.openweathermap.org/data/2.5/forecast", body = Owm.forecast)
        }
        val snapshot = repo(fake).fetch(kansas, SourceConfig(owmApiKey = "0123456789abcdef0123456789abcdef"), FetchPlan.FULL)
        assertEquals(DataSource.OWM, snapshot.current!!.source)
        assertEquals(DataSource.OWM, snapshot.forecastSource)
        assertEquals(3, snapshot.alerts.size, "alerts still come from NWS")
        val owmRequest = fake.requests.first { it.url.host == "api.openweathermap.org" }
        assertEquals("39.7456", owmRequest.url.queryParameter("lat"))
        assertEquals("no-store", owmRequest.header("Cache-Control"))
    }

    @Test
    fun outsideUsUsesOwm() = runTest {
        val fake = FakeNetwork().apply {
            on("/points/51.5085,-0.1257", code = 404, body = "{}")
            on("api.openweathermap.org/data/2.5/weather", body = Owm.current)
            on("api.openweathermap.org/data/2.5/forecast", body = Owm.forecast)
        }
        val snapshot = repo(fake).fetch(london, SourceConfig(owmApiKey = "0123456789abcdef0123456789abcdef"), FetchPlan.FULL)
        assertEquals(DataSource.OWM, snapshot.current!!.source)
        assertEquals("London", snapshot.placeName)
        assertFalse(snapshot.alertsAvailable)
        assertNull(snapshot.radarStation)
    }

    @Test
    fun outsideUsWithoutKeyExplainsWhatToDo() = runTest {
        val fake = FakeNetwork().apply { on("/points/", code = 404, body = "{}") }
        val error = assertFailsWith<WeatherUnavailableException> {
            repo(fake).fetch(london, SourceConfig(), FetchPlan.FULL)
        }
        assertTrue(error.issues.any { "OpenWeatherMap API key" in it.message }, "${error.issues}")
    }
}
