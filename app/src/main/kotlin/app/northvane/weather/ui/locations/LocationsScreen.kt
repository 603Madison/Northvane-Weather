@file:OptIn(ExperimentalMaterial3Api::class)

package app.northvane.weather.ui.locations

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.northvane.core.model.GeoPoint
import app.northvane.core.model.SavedLocation
import app.northvane.core.owm.PlaceResult
import app.northvane.weather.AppContainer
import app.northvane.weather.ui.MainUiState
import app.northvane.weather.ui.MainViewModel
import app.northvane.weather.ui.home.LOCATION_PERMISSIONS
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun LocationsScreen(state: MainUiState, vm: MainViewModel, container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchedOnce by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var pendingDelete by remember { mutableStateOf<SavedLocation?>(null) }
    var locating by remember { mutableStateOf(false) }
    // The location callback can arrive after the user already left this screen; don't navigate then.
    var onScreen by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { onScreen = false } }
    val finishIfStillHere: (Boolean) -> Unit = { ok ->
        locating = false
        if (ok && onScreen) onBack()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            locating = true
            vm.useDeviceLocation(finishIfStillHere)
        }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.length < 2) return
        focus.clearFocus()
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true
            results = container.placeSearch.search(q)
            searchedOnce = true
            searching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locations") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(100) },
                    singleLine = true,
                    label = { Text("City, ZIP code, or \"lat, lon\"") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                results = emptyList()
                                searchedOnce = false
                            }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (searching) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
            items(results, key = { "${it.latitude},${it.longitude},${it.name}" }) { place ->
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = { Text("${GeoPoint.format(place.latitude)}, ${GeoPoint.format(place.longitude)}") },
                    leadingContent = { Icon(Icons.Filled.Place, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.Add, contentDescription = "Add") },
                    modifier = Modifier.clickable {
                        vm.addPlace(place)
                        onBack()
                    },
                )
            }
            if (searchedOnce && !searching && results.isEmpty()) {
                item {
                    Text(
                        "No matches. Try a nearby city, a ZIP code, or coordinates like 39.74, -97.08.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            locating = true
                            vm.useDeviceLocation(finishIfStillHere)
                        } else {
                            permission.launch(LOCATION_PERMISSIONS)
                        }
                    },
                    enabled = !locating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (locating) "Finding you…" else "Use my current location")
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Saved", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.locations.isEmpty()) {
                item {
                    Text("No saved locations yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.locations, key = { it.id }) { location ->
                ListItem(
                    headlineContent = { Text(location.name) },
                    supportingContent = {
                        Text(
                            if (location.isDeviceLocation) "Your location"
                            else "${GeoPoint.format(location.latitude)}, ${GeoPoint.format(location.longitude)}",
                        )
                    },
                    leadingContent = {
                        RadioButton(selected = location.id == state.selected?.id, onClick = { vm.selectLocation(location.id) })
                    },
                    trailingContent = {
                        IconButton(onClick = { pendingDelete = location }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove ${location.name}")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable { vm.selectLocation(location.id) },
                )
            }
        }
    }

    pendingDelete?.let { location ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${location.name}?") },
            text = { Text("Its cached weather and alert notifications will stop. Any WeeWX station linked to it will be unlinked.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeLocation(location.id)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}
