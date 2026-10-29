@file:OptIn(ExperimentalMaterial3Api::class)

package app.northvane.weather.ui.traffic

import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.northvane.core.model.UnitSystem
import app.northvane.core.model.Units
import app.northvane.core.traffic.LaneImpact
import app.northvane.core.traffic.TrafficEvent
import app.northvane.core.traffic.TrafficEventType
import app.northvane.weather.AppContainer
import app.northvane.weather.data.SatelliteFeature
import app.northvane.weather.ui.MainUiState
import app.northvane.weather.ui.TimeFormat
import app.northvane.weather.ui.components.NorthvaneIcons
import app.northvane.weather.ui.theme.LocalIsDark
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun TrafficScreen(state: MainUiState, container: AppContainer, onOpenSettings: () -> Unit) {
    val vm: TrafficViewModel = viewModel(factory = TrafficViewModel.factory(container))
    val traffic by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dark = LocalIsDark.current
    val location = state.selected
    val settings = state.settings
    var loadAnyway by rememberSaveable { mutableStateOf(false) }
    var focus by remember { mutableStateOf<TrafficEvent?>(null) }
    val zone = remember(state.snapshot?.timeZoneId) {
        state.snapshot?.timeZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    }
    val time = remember(zone) { TimeFormat(zone, DateFormat.is24HourFormat(context)) }
    val stateCode = TrafficViewModel.stateCodeOf(state.snapshot?.placeName ?: location?.name)
    val paused = state.liteMode && SatelliteFeature.TRAFFIC !in settings.satelliteFeatures && !loadAnyway
    val flowAllowed = !state.liteMode || SatelliteFeature.TRAFFIC_FLOW in settings.satelliteFeatures

    if (location != null && !paused) {
        LaunchedEffect(location.id, location.latitude, location.longitude, settings.trafficRadiusKm, settings.trafficFlowLayer, flowAllowed, dark) {
            vm.load(location, stateCode, settings.trafficRadiusKm, settings.trafficFlowLayer && flowAllowed, dark)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Traffic")
                        location?.let {
                            Text(
                                "${it.displayName} · within ${distance(settings.trafficRadiusKm, settings.units)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (location != null && !paused) {
                        IconButton(onClick = {
                            vm.load(location, stateCode, settings.trafficRadiusKm, settings.trafficFlowLayer && flowAllowed, dark, force = true)
                        }) { Icon(Icons.Filled.Refresh, contentDescription = "Reload traffic") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                location == null -> Centered("Add a location on the Today tab to see traffic.")
                paused -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(NorthvaneIcons.Satellite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Traffic paused on satellite", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "State work-zone feeds can be several hundred KB, so traffic is off in your satellite settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { loadAnyway = true }) { Text("Load traffic anyway") }
                    TextButton(onClick = onOpenSettings) { Text("Satellite settings") }
                }
                else -> {
                    val events = traffic.snapshot?.events.orEmpty()
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(0.45f)) {
                            TrafficMap(
                                center = location.point,
                                events = events,
                                focus = focus,
                                flowTiles = traffic.flowTiles,
                                dark = dark,
                                zoom = zoomFor(settings.trafficRadiusKm),
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (traffic.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                        }
                        TrafficEventList(traffic, settings.units, stateCode, time, onFocus = { focus = it }, modifier = Modifier.weight(0.55f))
                    }
                }
            }
        }
    }
}

/** Legend, then active and upcoming events nearest first, then sources and gaps. */
@Composable
internal fun TrafficEventList(
    traffic: TrafficUiState,
    units: UnitSystem,
    stateCode: String?,
    time: TimeFormat,
    onFocus: (TrafficEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = Instant.now()
    val events = traffic.snapshot?.events.orEmpty()
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Legend(traffic.flowTiles != null) }
        traffic.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
        val active = events.filter { it.isActive(now) }
        val upcoming = events.filterNot { it.isActive(now) }
        if (traffic.snapshot != null && events.isEmpty()) {
            item {
                Text("No reported incidents, closures or work zones nearby.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        if (active.isNotEmpty()) item { Header("Now (${active.size})") }
        items(active, key = { "a-" + it.id }) { e -> EventCard(e, units, time) { onFocus(e) } }
        if (upcoming.isNotEmpty()) item { Header("Starting this week (${upcoming.size})") }
        items(upcoming, key = { "u-" + it.id }) { e -> EventCard(e, units, time) { onFocus(e) } }
        item { SourcesNote(traffic, stateCode) }
    }
}

private fun zoomFor(radiusKm: Double): Double = when {
    radiusKm <= 10 -> 11.5
    radiusKm <= 25 -> 10.5
    radiusKm <= 45 -> 9.5
    radiusKm <= 85 -> 8.5
    else -> 7.5
}

internal fun distance(km: Double, units: UnitSystem): String =
    if (units == UnitSystem.IMPERIAL) {
        val mi = Units.kmToMiles(km)
        if (mi < 10) "%.1f mi".format(mi) else "${mi.roundToInt()} mi"
    } else {
        if (km < 10) "%.1f km".format(km) else "${km.roundToInt()} km"
    }

@Composable
private fun Header(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun Centered(text: String) = Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Dot(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(10.dp)) { drawCircle(color) }

private fun color(type: TrafficEventType) = Color(eventColor(type).toColorInt())

@Composable
private fun Legend(flow: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(TrafficEventType.CRASH to "Crash", TrafficEventType.CLOSURE to "Closed", TrafficEventType.WORK_ZONE to "Work zone").forEach { (t, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color(t))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (flow) Text("· Road colors: speed vs. normal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EventCard(e: TrafficEvent, units: UnitSystem, time: TimeFormat, onFocus: () -> Unit) {
    var open by rememberSaveable(e.id) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable {
            open = !open
            onFocus()
        },
    ) {
        Column(Modifier.padding(14.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color(e.type))
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(e.road, e.direction?.replaceFirstChar { it.uppercase() }).joinToString(" · ").ifEmpty { e.type.label },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(distance(e.distanceKm, units), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val status = listOfNotNull(
                e.type.label,
                e.impact.label.takeIf { e.impact != LaneImpact.UNKNOWN && e.impact != LaneImpact.OPEN && e.type != TrafficEventType.CLOSURE },
                e.end?.let { "until ${time.smart(it)}" } ?: e.start?.takeIf { it.isAfter(Instant.now()) }?.let { "starts ${time.smart(it)}" },
            ).joinToString(" · ")
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            e.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (open) 30 else 2,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (open) {
                Text(e.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SourcesNote(state: TrafficUiState, stateCode: String?) {
    val lines = buildList {
        state.snapshot?.sources?.takeIf { it.isNotEmpty() }?.let { add("Sources: " + it.joinToString(", ")) }
        if (!state.hasNe511Key && stateCode in app.northvane.core.traffic.TrafficClient.NE511_STATES) {
            add("Crashes and closures need a free New England 511 key (Settings › Data sources). Work zones need nothing.")
        }
        if (state.flowTiles == null) add("Live road speeds are optional: add a TomTom key in Data sources (free tier).")
        state.snapshot?.issues?.forEach { add(it) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
