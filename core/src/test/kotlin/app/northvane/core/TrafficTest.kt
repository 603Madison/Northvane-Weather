package app.northvane.core

import app.northvane.core.model.GeoPoint
import app.northvane.core.net.HttpClients
import app.northvane.core.net.InvalidCredentialsException
import app.northvane.core.traffic.LaneImpact
import app.northvane.core.traffic.TrafficClient
import app.northvane.core.traffic.TrafficEventType
import app.northvane.core.traffic.TrafficService
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TrafficTest {
    private fun resource(name: String) = requireNotNull(javaClass.getResource(name)) { "missing $name" }.readText()
    private val portland = GeoPoint(43.6591, -70.2568)
    private val now = Instant.parse("2026-10-29T18:00:00Z")

    @Test
    fun registryKeepsKeylessActiveFeeds() {
        val feeds = TrafficClient.parseRegistry(resource("/traffic/wzdx_registry.json"))
        assertTrue(feeds.none { it.url.endsWith("key=") || it.url.contains("INSERT", ignoreCase = true) })
        val client = TrafficClient(HttpClients.create("t", null))
        val maine = client.feedsFor("ME", feeds)
        assertEquals(listOf("https://api.dx.ne-compass.com/wzdx-latest/"), maine.map { it.url })
        assertEquals(maine, client.feedsFor("nh", feeds))
        assertTrue(client.feedsFor("RI", feeds).isEmpty())
        assertTrue(client.feedsFor(null, feeds).isEmpty())
    }

    @Test
    fun parsesNorthernNewEnglandWorkZones() {
        val events = TrafficClient.parseWzdx(resource("/traffic/wzdx_ne_compass.json"), portland, 40.0, now, "NHDOT/VTAOT/MEDOT")
        assertTrue(events.size >= 5, "${events.size}")
        assertTrue(events.zipWithNext().all { (a, b) -> a.distanceKm <= b.distanceKm })
        assertTrue(events.all { it.distanceKm <= 40.0 })
        assertTrue(events.none { it.road == "US-5" }, "Brattleboro is far away")
        val i95 = events.first { it.road == "I 95 SB" }
        assertEquals(TrafficEventType.WORK_ZONE, i95.type)
        assertEquals(LaneImpact.SOME_CLOSED, i95.impact)
        assertEquals("southbound", i95.direction)
        assertTrue(i95.path.size in 2..40)
        // Ended work zones are dropped.
        val later = TrafficClient.parseWzdx(resource("/traffic/wzdx_ne_compass.json"), portland, 40.0, Instant.parse("2026-11-20T00:00:00Z"), "x")
        assertTrue(later.size < events.size)
    }

    @Test
    fun parsesNewEngland511Events() {
        val json = """
        [{"ID":"NE-1","SourceId":"x","Organization":"MaineDOT","RoadwayName":"I-295","DirectionOfTravel":"Northbound",
          "Description":"Crash blocking the right lane at Exit 7","Reported":1793300000,"LastUpdated":1793300400,
          "StartDate":1793300000,"PlannedEndDate":null,"LanesAffected":"1 Right Lane Closed","Latitude":43.6669,"Longitude":-70.2611,
          "EventType":"accidentsAndIncidents","EventSubType":"Crash","IsFullClosure":false,"Severity":"Major"},
         {"Id":"NE-2","RoadwayName":"US-1","DirectionOfTravel":"Both Directions","Description":"Road closed for bridge work",
          "StartDate":1793200000,"PlannedEndDate":1893200000,"Latitude":43.70,"Longitude":-70.30,"EventType":"closures","IsFullClosure":true},
         {"ID":"NE-3","RoadwayName":"I-89","Latitude":44.5,"Longitude":-72.6,"EventType":"roadwork"}]
        """.trimIndent()
        val events = TrafficClient.parse511(json, portland, 40.0, now, "New England 511")
        assertEquals(listOf("NE-1", "NE-2"), events.map { it.id })
        assertEquals(TrafficEventType.CRASH, events[0].type)
        assertEquals(LaneImpact.SOME_CLOSED, events[0].impact)
        assertEquals(Instant.ofEpochSecond(1793300000), events[0].start)
        assertEquals(TrafficEventType.CLOSURE, events[1].type)
        assertEquals(LaneImpact.ALL_CLOSED, events[1].impact)
    }

    @Test
    fun serviceCombinesSourcesAndExplainsGaps() = runTest {
        val fake = FakeNetwork().apply {
            on("datahub.transportation.gov", body = resource("/traffic/wzdx_registry.json"))
            on("api.dx.ne-compass.com/wzdx-latest", body = resource("/traffic/wzdx_ne_compass.json"))
            on("newengland511.org/api/v2/get/event", code = 400, body = "<Error><Message>Invalid Key</Message></Error>")
        }
        val http = HttpClients.create("t", null).newBuilder().addInterceptor(fake).build()
        val client = TrafficClient(http)
        val service = TrafficService(client)
        val snapshot = service.load("ME", portland, 40.0, ne511Key = "bad-key-0123456789", registry = { client.registry() }, now = now)
        assertTrue(snapshot.events.isNotEmpty())
        assertEquals(listOf("NHDOT/VTAOT/MEDOT (WZDx)"), snapshot.sources)
        assertTrue(snapshot.issues.single().startsWith("New England 511:"), "${snapshot.issues}")
        val ne511 = fake.requests.single { it.url.host == "newengland511.org" }
        assertEquals("no-store", ne511.header("Cache-Control"))
        // Rhode Island: nothing keyless covers it.
        val ri = service.load("RI", GeoPoint(41.82, -71.41), 40.0, null, { client.registry() }, now)
        assertTrue(ri.events.isEmpty() && ri.issues.single().contains("No free government traffic feed"))
        assertFailsWith<InvalidCredentialsException> { client.ne511Events("bad-key-0123456789", portland, 40.0, now) }
    }

    @Test
    fun tomTomTileTemplate() {
        val url = TrafficClient.tomTomFlowTiles("abcDEF0123456789abcDEF0123456789", dark = true)
        assertTrue(url.startsWith("https://api.tomtom.com/traffic/map/4/tile/flow/relative0-dark/{z}/{x}/{y}.png?key="))
    }
}
