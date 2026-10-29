package app.northvane.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.northvane.core.model.SavedLocation
import app.northvane.core.model.WeatherSnapshot
import app.northvane.core.net.toUserMessage
import app.northvane.core.owm.PlaceResult
import app.northvane.core.util.Solar
import app.northvane.weather.AppContainer
import app.northvane.weather.data.AppSettings
import app.northvane.weather.data.LocationStore
import app.northvane.weather.data.SecretStore
import app.northvane.weather.net.NetworkStatus
import app.northvane.weather.net.isLimited
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class MainUiState(
    val ready: Boolean = false,
    val locations: List<SavedLocation> = emptyList(),
    val selected: SavedLocation? = null,
    val snapshot: WeatherSnapshot? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val settings: AppSettings = AppSettings(),
    val network: NetworkStatus = NetworkStatus(),
    val liteMode: Boolean = false,
    /** Whether a Google Pollen API key is saved (the key itself never leaves the secret store). */
    val pollenKeySet: Boolean = false,
)

/** Activity-scoped state shared by the Today, Radar and Alerts tabs. */
class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val snapshots = MutableStateFlow<Map<String, WeatherSnapshot>>(emptyMap())
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null
    private var refreshingId: String? = null

    private val inputs = combine(
        container.locations.locations,
        container.settings.settings,
        container.connectivity.status,
    ) { locations, settings, network -> Triple(locations, settings, network) }

    val state: StateFlow<MainUiState> = combine(inputs, snapshots, refreshing, error) { input, snaps, isRefreshing, err ->
        val (locations, settings, network) = input
        val selected = locations.firstOrNull { it.id == settings.selectedLocationId } ?: locations.firstOrNull()
        MainUiState(
            ready = true,
            locations = locations,
            selected = selected,
            snapshot = selected?.let { snaps[it.id] },
            refreshing = isRefreshing,
            error = err,
            settings = settings,
            network = network,
            liteMode = isLimited(settings, network),
            pollenKeySet = settings.pollenEnabled && container.secrets.has(SecretStore.POLLEN_API_KEY),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    init {
        viewModelScope.launch {
            state.map { it.selected }.filterNotNull().distinctUntilChanged { a, b -> a.id == b.id }.collect { location ->
                error.value = null
                loadCachedThenMaybeRefresh(location)
            }
        }
    }

    private suspend fun loadCachedThenMaybeRefresh(location: SavedLocation) {
        val cached = snapshots.value[location.id] ?: container.weather.cached(location.id)?.also { snapshot ->
            snapshots.update { it + (location.id to snapshot) }
        }
        val interval = container.weather.refreshInterval(container.weather.currentPlan(container.settings.settings.first()))
        if (cached == null || Duration.between(cached.fetchedAt, Instant.now()) > interval) refresh(location)
    }

    /** Called when the app returns to the foreground. */
    fun onForeground() {
        val location = state.value.selected ?: return
        viewModelScope.launch { loadCachedThenMaybeRefresh(location) }
    }

    fun refresh(requested: SavedLocation? = state.value.selected) {
        val location: SavedLocation = requested ?: return
        if (refreshJob?.isActive == true) {
            if (refreshingId == location.id) return
            refreshJob?.cancel()
        }
        refreshingId = location.id
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            error.value = null
            try {
                var target: SavedLocation = location
                if (location.isDeviceLocation) {
                    container.deviceLocation.current()?.let { p ->
                        if (Solar.distanceKm(location.latitude, location.longitude, p.latitude, p.longitude) > 1.0) {
                            target = location.copy(latitude = p.latitude, longitude = p.longitude)
                            container.locations.upsertLocation(target)
                        }
                    }
                }
                val snapshot = container.weather.refresh(target)
                snapshots.update { it + (target.id to snapshot) }
                val place = snapshot.placeName
                if (target.isDeviceLocation && place != null && place != target.name) {
                    container.locations.upsertLocation(target.copy(name = place))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.toUserMessage()
            } finally {
                refreshing.value = false
            }
        }
    }

    fun selectLocation(id: String) {
        viewModelScope.launch { container.settings.setSelectedLocation(id) }
    }

    fun addPlace(place: PlaceResult) {
        viewModelScope.launch {
            val location = SavedLocation(UUID.randomUUID().toString(), place.name, place.latitude, place.longitude)
            container.locations.upsertLocation(location)
            container.settings.setSelectedLocation(location.id)
        }
    }

    /** Requires location permission (precise or approximate) to already be granted. */
    fun useDeviceLocation(onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val point = container.deviceLocation.current()
            if (point == null) {
                error.value = "Couldn't determine your location. Check that location services are on."
                onDone(false)
                return@launch
            }
            val existing = state.value.locations.firstOrNull { it.isDeviceLocation }
            val location = SavedLocation(
                id = LocationStore.DEVICE_LOCATION_ID,
                name = existing?.name ?: "Current location",
                latitude = point.latitude,
                longitude = point.longitude,
                isDeviceLocation = true,
            )
            container.locations.upsertLocation(location)
            container.settings.setSelectedLocation(location.id)
            onDone(true)
            refresh(location)
        }
    }

    fun removeLocation(id: String) {
        viewModelScope.launch {
            container.locations.removeLocation(id)
            container.weatherCache.delete(id)
            snapshots.update { it - id }
        }
    }

    fun renameLocation(id: String, nickname: String?) {
        viewModelScope.launch { container.locations.setNickname(id, nickname) }
    }

    fun setLocationNotify(id: String, enabled: Boolean) {
        viewModelScope.launch { container.locations.setNotify(id, enabled) }
    }

    fun dismissError() {
        error.value = null
    }

    fun markNotificationPromptShown() {
        viewModelScope.launch { container.settings.setNotificationPromptShown() }
    }

    /** Run an alert check right away (e.g. just after notification permission is granted). */
    fun checkAlertsNow() = container.alertScheduler.checkNow()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(container) }
        }
    }
}
