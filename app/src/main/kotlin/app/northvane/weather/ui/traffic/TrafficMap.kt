package app.northvane.weather.ui.traffic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.northvane.core.model.GeoPoint
import app.northvane.core.traffic.TrafficEvent
import app.northvane.core.traffic.TrafficEventType
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/positron"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

internal fun eventColor(type: TrafficEventType): String = when (type) {
    TrafficEventType.CRASH -> "#D32F2F"
    TrafficEventType.CLOSURE -> "#7B1F1F"
    TrafficEventType.WORK_ZONE -> "#EF7D1A"
    TrafficEventType.CONGESTION -> "#C62828"
    TrafficEventType.ROAD_CONDITIONS -> "#1E88E5"
    TrafficEventType.SPECIAL_EVENT -> "#8E44AD"
    TrafficEventType.OTHER -> "#546E7A"
}

/** Traffic map: optional TomTom speed layer, government road events on top, and a location pin. */
@Composable
fun TrafficMap(
    center: GeoPoint,
    events: List<TrafficEvent>,
    focus: TrafficEvent?,
    flowTiles: String?,
    dark: Boolean,
    zoom: Double,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val state = lifecycleOwner.lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.setRotateGesturesEnabled(false)
            m.uiSettings.setTiltGesturesEnabled(false)
            m.uiSettings.setCompassEnabled(false)
            m.uiSettings.setLogoEnabled(false)
            m.setMinZoomPreference(5.0)
            m.setMaxZoomPreference(16.0)
            m.cameraPosition = CameraPosition.Builder().target(LatLng(center.latitude, center.longitude)).zoom(zoom).build()
            map = m
        }
    }

    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromUri(if (dark) DARK_STYLE else LIGHT_STYLE)) { loaded -> style = loaded }
    }

    LaunchedEffect(map, center, zoom) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(center.latitude, center.longitude), zoom))
    }

    LaunchedEffect(map, focus) {
        val f = focus ?: return@LaunchedEffect
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(f.latitude, f.longitude), 13.0))
    }

    LaunchedEffect(style, flowTiles) {
        val s = style ?: return@LaunchedEffect
        s.removeLayer("flow-layer")
        s.removeSource("flow-src")
        val url = flowTiles ?: return@LaunchedEffect
        val tiles = TileSet("2.2.0", url)
        tiles.setMinZoom(0f)
        tiles.setMaxZoom(18f)
        s.addSource(RasterSource("flow-src", tiles, 256))
        val layer = RasterLayer("flow-layer", "flow-src").withProperties(PropertyFactory.rasterOpacity(0.85f))
        val label = s.layers.firstOrNull { it is SymbolLayer }?.id
        if (label != null) s.addLayerBelow(layer, label) else s.addLayer(layer)
    }

    LaunchedEffect(style, events, center) {
        val s = style ?: return@LaunchedEffect
        listOf("event-points", "event-lines", "pin-halo", "pin-dot").forEach { s.removeLayer(it) }
        listOf("event-src", "pin-src").forEach { s.removeSource(it) }
        val features = events.flatMap { e ->
            val point = Feature.fromGeometry(Point.fromLngLat(e.longitude, e.latitude)).apply { addStringProperty("color", eventColor(e.type)) }
            val line = e.path.takeIf { it.size >= 2 }?.let { path ->
                Feature.fromGeometry(LineString.fromLngLats(path.map { Point.fromLngLat(it[0], it[1]) })).apply {
                    addStringProperty("color", eventColor(e.type))
                }
            }
            listOfNotNull(line, point)
        }
        s.addSource(GeoJsonSource("event-src", FeatureCollection.fromFeatures(features)))
        s.addLayer(
            LineLayer("event-lines", "event-src").withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(0.85f),
            ).withFilter(Expression.eq(Expression.geometryType(), Expression.literal("LineString"))),
        )
        s.addLayer(
            CircleLayer("event-points", "event-src").withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(Expression.get("color")),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ).withFilter(Expression.eq(Expression.geometryType(), Expression.literal("Point"))),
        )
        s.addSource(GeoJsonSource("pin-src", Feature.fromGeometry(Point.fromLngLat(center.longitude, center.latitude))))
        s.addLayer(
            CircleLayer("pin-halo", "pin-src").withProperties(
                PropertyFactory.circleRadius(15f), PropertyFactory.circleColor("#2563EB"), PropertyFactory.circleOpacity(0.22f),
            ),
        )
        s.addLayer(
            CircleLayer("pin-dot", "pin-src").withProperties(
                PropertyFactory.circleRadius(7f), PropertyFactory.circleColor("#2563EB"),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
