@file:OptIn(ExperimentalMaterial3Api::class)

package app.northvane.weather.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.northvane.weather.AppContainer
import app.northvane.weather.ui.alerts.AlertsScreen
import app.northvane.weather.ui.components.NorthvaneIcons
import app.northvane.weather.ui.home.HomeActions
import app.northvane.weather.ui.home.HomeScreen
import app.northvane.weather.ui.locations.LocationsScreen
import app.northvane.weather.ui.radar.RadarScreen
import app.northvane.weather.ui.settings.DataSourcesScreen
import app.northvane.weather.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.StateFlow

sealed interface NavRequest {
    data class OpenAlerts(val locationId: String?) : NavRequest
}

object Routes {
    const val TODAY = "today"
    const val RADAR = "radar"
    const val ALERTS = "alerts"
    const val LOCATIONS = "locations"
    const val SETTINGS = "settings"
    const val SOURCES = "sources"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Filled.Home),
    Tab(Routes.RADAR, "Radar", NorthvaneIcons.Radar),
    Tab(Routes.ALERTS, "Alerts", Icons.Filled.Warning),
)

private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun NorthvaneRoot(
    vm: MainViewModel,
    container: AppContainer,
    navRequests: StateFlow<NavRequest?>,
    onNavRequestHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val request by navRequests.collectAsStateWithLifecycle()
    val homeActions = remember(vm) {
        HomeActions(
            refresh = { vm.refresh() },
            selectLocation = vm::selectLocation,
            useDeviceLocation = { vm.useDeviceLocation() },
            dismissError = vm::dismissError,
            markNotificationPromptShown = vm::markNotificationPromptShown,
            checkAlertsNow = vm::checkAlertsNow,
        )
    }

    LaunchedEffect(request) {
        when (val r = request) {
            is NavRequest.OpenAlerts -> {
                r.locationId?.let { id -> if (state.locations.any { it.id == id } || !state.ready) vm.selectLocation(id) }
                navController.navigateToTab(Routes.ALERTS)
                onNavRequestHandled()
            }
            null -> Unit
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route == null || tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = {
                                val count = if (tab.route == Routes.ALERTS) state.snapshot?.alerts?.size ?: 0 else 0
                                BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.TODAY) {
                HomeScreen(
                    state = state,
                    actions = homeActions,
                    onOpenAlerts = { navController.navigateToTab(Routes.ALERTS) },
                    onOpenLocations = { navController.navigate(Routes.LOCATIONS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenSources = { navController.navigate(Routes.SOURCES) },
                )
            }
            composable(Routes.RADAR) {
                RadarScreen(state = state, container = container, onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(Routes.ALERTS) {
                AlertsScreen(
                    state = state,
                    onRefresh = { vm.refresh() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.LOCATIONS) {
                LocationsScreen(state = state, vm = vm, container = container, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = state,
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenSources = { navController.navigate(Routes.SOURCES) },
                )
            }
            composable(Routes.SOURCES) {
                DataSourcesScreen(
                    state = state,
                    container = container,
                    onBack = { navController.popBackStack() },
                    onSourcesChanged = { vm.refresh() },
                )
            }
        }
    }
}
