package app.northvane.weather.ui.radar

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
import app.northvane.core.radar.RadarFrame
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

// Free, keyless vector basemaps (OpenStreetMap data) — no Google/Mapbox key needed.
private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/positron"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val RADAR_OPACITY = 0.78f
private const val DEFAULT_ZOOM = 6.5

/**
 * MapLibre map with one raster layer per radar frame. All frames load up front
 * (at zero opacity) so animation is just an opacity swap, with no flicker.
 */
@Composable
fun RadarMap(
    center: GeoPoint,
    frames: List<RadarFrame>,
    index: Int,
    dark: Boolean,
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
    var layerIds by remember { mutableStateOf<List<String>>(emptyList()) }

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
            m.setMinZoomPreference(3.0)
            m.setMaxZoomPreference(10.0)
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(center.latitude, center.longitude))
                .zoom(DEFAULT_ZOOM)
                .build()
            map = m
        }
    }

    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromUri(if (dark) DARK_STYLE else LIGHT_STYLE)) { loaded -> style = loaded }
    }

    LaunchedEffect(map, center) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(center.latitude, center.longitude), DEFAULT_ZOOM))
    }

    LaunchedEffect(style, frames) {
        val s = style ?: return@LaunchedEffect
        layerIds.forEach { id ->
            s.removeLayer(id)
            s.removeSource(id.replace("radar-layer-", "radar-src-"))
        }
        // Keep place labels readable by drawing radar beneath the first label layer.
        val labelLayer = s.layers.firstOrNull { it is SymbolLayer }?.id
        layerIds = frames.mapIndexed { i, frame ->
            val sourceId = "radar-src-$i"
            val layerId = "radar-layer-$i"
            val tiles = TileSet("2.2.0", frame.tileUrl)
            tiles.setMinZoom(0f)
            tiles.setMaxZoom(10f)
            s.addSource(RasterSource(sourceId, tiles, 256))
            val layer = RasterLayer(layerId, sourceId).withProperties(
                PropertyFactory.rasterOpacity(0f),
                PropertyFactory.rasterFadeDuration(0f),
            )
            if (labelLayer != null) s.addLayerBelow(layer, labelLayer) else s.addLayer(layer)
            layerId
        }
    }

    LaunchedEffect(style, layerIds, index) {
        val s = style ?: return@LaunchedEffect
        layerIds.forEachIndexed { i, id ->
            s.getLayer(id)?.setProperties(PropertyFactory.rasterOpacity(if (i == index) RADAR_OPACITY else 0f))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
