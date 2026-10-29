@file:OptIn(ExperimentalMaterial3Api::class)

package app.northvane.weather.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.northvane.core.model.AlertCategory
import app.northvane.core.model.UnitSystem
import app.northvane.core.radar.RadarSource
import app.northvane.weather.AppContainer
import app.northvane.weather.BuildConfig
import app.northvane.weather.data.DataSaverMode
import app.northvane.weather.data.SatelliteFeature
import app.northvane.weather.data.SettingsRepository
import app.northvane.weather.data.ThemeMode
import app.northvane.weather.notify.AlertNotifier
import app.northvane.weather.ui.MainUiState
import app.northvane.weather.ui.components.LabeledSwitch
import app.northvane.weather.ui.components.SectionCard
import app.northvane.weather.ui.components.SegmentedChoice
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    state: MainUiState,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = state.settings

    fun update(block: suspend SettingsRepository.() -> Unit) {
        scope.launch { container.settings.block() }
    }

    fun updateAlerts(block: suspend SettingsRepository.() -> Unit) {
        scope.launch {
            container.settings.block()
            container.alertScheduler.sync()
        }
    }

    var testSent by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(AlertNotifier.canNotify(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsAllowed = AlertNotifier.canNotify(context) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = AlertNotifier.canNotify(context)
        if (granted) container.alertScheduler.checkNow()
    }
    val openNotificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionCard("Appearance") {
                Label("Theme")
                SegmentedChoice(
                    options = ThemeMode.entries,
                    selected = s.themeMode,
                    label = {
                        when (it) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        }
                    },
                    onSelect = { mode -> update { setThemeMode(mode) } },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    LabeledSwitch(
                        title = "Wallpaper colors",
                        subtitle = "Use Material You dynamic color",
                        checked = s.dynamicColor,
                        onCheckedChange = { enabled -> update { setDynamicColor(enabled) } },
                    )
                }
                Label("Units")
                SegmentedChoice(
                    options = listOf(UnitSystem.IMPERIAL, UnitSystem.METRIC),
                    selected = s.units,
                    label = { if (it == UnitSystem.IMPERIAL) "°F · mph · in" else "°C · km/h · mm" },
                    onSelect = { units -> update { setUnits(units) } },
                )
            }

            SectionCard("Severe weather alerts") {
                LabeledSwitch(
                    title = "Alert notifications",
                    subtitle = "Checks the National Weather Service for all saved locations in the background",
                    checked = s.alertsEnabled,
                    onCheckedChange = { enabled -> updateAlerts { setAlertsEnabled(enabled) } },
                )
                if (s.alertsEnabled) {
                    if (!notificationsAllowed) {
                        Text(
                            "Notifications are blocked for Northvane, so alerts can't reach you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        FilledTonalButton(onClick = {
                            val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            if (needsRuntimePermission) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openNotificationSettings()
                            }
                        }) { Text("Allow notifications") }
                    }
                    Label("Notify me about")
                    CategoryRow(AlertCategory.WARNING, "Warnings", "Tornado, severe thunderstorm, flash flood, winter storm…", s.alertCategories) { c, on ->
                        updateAlerts { setAlertCategory(c, on) }
                    }
                    CategoryRow(AlertCategory.WATCH, "Watches", "Conditions favorable for hazardous weather", s.alertCategories) { c, on ->
                        updateAlerts { setAlertCategory(c, on) }
                    }
                    CategoryRow(AlertCategory.ADVISORY, "Advisories", "Wind, heat, frost, dense fog…", s.alertCategories) { c, on ->
                        updateAlerts { setAlertCategory(c, on) }
                    }
                    CategoryRow(AlertCategory.STATEMENT, "Statements", "Special weather statements and outlooks", s.alertCategories) { c, on ->
                        updateAlerts {
                            setAlertCategory(c, on)
                            setAlertCategory(AlertCategory.OTHER, on)
                        }
                    }
                    Label("Check every")
                    SegmentedChoice(
                        options = listOf(15, 30, 60),
                        selected = s.alertCheckMinutes,
                        label = { "$it min" },
                        onSelect = { minutes -> updateAlerts { setAlertInterval(minutes) } },
                    )
                    Text(
                        "Android may delay background checks to save battery. Warnings use a high-priority channel; adjust sounds per channel in system settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = {
                            val sent = AlertNotifier.sendTest(context, state.selected?.id, state.selected?.name)
                            if (!sent) {
                                val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                if (needsRuntimePermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings()
                            }
                            testSent = sent
                        }) { Text("Send test notification") }
                        TextButton(onClick = openNotificationSettings) { Text("Channels") }
                    }
                    if (testSent) {
                        Text(
                            "Test sent. If it didn't appear, check the Warnings channel and Do Not Disturb.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionCard("Forecast") {
                LabeledSwitch(
                    title = "Power outage outlook",
                    subtitle = "Rough daily estimate from forecast gusts, ice, wet snow, saturated ground, thunderstorms " +
                        "and NWS alerts, weighted for New England's tree-lined power lines and leaf-on season. " +
                        "Not a utility forecast.",
                    checked = s.outageRiskEnabled,
                    onCheckedChange = { enabled -> update { setOutageRisk(enabled) } },
                )
            }

            SectionCard("Data sources") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSources),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Weather sources & API keys", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "NWS (always on) · WeeWX local station · OpenWeatherMap fallback" +
                                if (s.owmEnabled) " (on)" else " (off)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }

            SectionCard("Satellite & data saver") {
                Text(
                    "Choose exactly what Northvane may download over satellite connections such as T-Mobile " +
                        "T-Satellite. Anything you turn off keeps showing its last downloaded data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Label("Apply these limits")
                SegmentedChoice(
                    options = DataSaverMode.entries,
                    selected = s.dataSaver,
                    label = {
                        when (it) {
                            DataSaverMode.AUTO -> "On satellite"
                            DataSaverMode.ALWAYS -> "Always"
                            DataSaverMode.OFF -> "Never"
                        }
                    },
                    onSelect = { mode -> update { setDataSaver(mode) } },
                )
                Label("Allowed over satellite")
                SatelliteFeature.entries.forEach { feature ->
                    val checked = feature in s.satelliteFeatures
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { update { setSatelliteFeature(feature, !checked) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { on -> update { setSatelliteFeature(feature, on) } })
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(feature.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                feature.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            feature.approxSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    "Over satellite, a WeeWX station can only be reached at a public internet address, not on your " +
                        "home network. Sizes are typical per refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val network = state.network
                Text(
                    "Current connection: " + when {
                        !network.connected -> "offline"
                        network.satellite -> "satellite" + if (network.constrained) " (bandwidth-limited)" else ""
                        network.constrained -> "bandwidth-limited"
                        network.metered -> "mobile data"
                        else -> "unmetered"
                    } + if (state.liteMode) " · satellite limits active" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("Radar") {
                Label("Past radar source")
                SegmentedChoice(
                    options = RadarSource.entries,
                    selected = s.radarSource,
                    label = { if (it == RadarSource.NOAA_MRMS) "NOAA MRMS" else "IEM NEXRAD" },
                    onSelect = { source -> update { setRadarSource(source) } },
                )
                LabeledSwitch(
                    title = "Future cast",
                    subtitle = "Simulated radar from NOAA's HRRR model (continental US)",
                    checked = s.futureCastEnabled,
                    onCheckedChange = { enabled -> update { setFutureCast(enabled) } },
                )
                if (s.futureCastEnabled) {
                    SegmentedChoice(
                        options = listOf(60, 120, 180),
                        selected = s.futureCastMinutes,
                        label = { "${it / 60} h ahead" },
                        onSelect = { minutes -> update { setFutureCastMinutes(minutes) } },
                    )
                }
            }

            SectionCard("About & privacy") {
                Text("Northvane ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "No accounts, ads, analytics or trackers. Your locations, settings and keys stay on this device and are " +
                        "excluded from backups. Weather requests send rounded coordinates (about 11 m) only to the services " +
                        "you use: weather.gov, your WeeWX server and, if enabled, OpenWeatherMap. Map tiles come from " +
                        "OpenFreeMap; radar from NOAA and the Iowa Environmental Mesonet. Place search uses your device's " +
                        "built-in geocoder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Map data © OpenStreetMap contributors, © OpenMapTiles. Weather data: NOAA / National Weather Service.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CategoryRow(
    category: AlertCategory,
    title: String,
    subtitle: String,
    enabled: Set<AlertCategory>,
    onChange: (AlertCategory, Boolean) -> Unit,
) {
    val checked = category in enabled
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(category, !checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onChange(category, it) })
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
