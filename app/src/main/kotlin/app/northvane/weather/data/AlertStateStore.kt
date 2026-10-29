package app.northvane.weather.data

import android.content.Context
import app.northvane.core.alerts.NotifiedAlerts
import app.northvane.core.json.NorthvaneJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Remembers which alerts have already produced a notification. */
class AlertStateStore(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, "notified_alerts.json")

    suspend fun read(): NotifiedAlerts = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext NotifiedAlerts()
        runCatching { NorthvaneJson.decodeFromString(NotifiedAlerts.serializer(), file.readText()) }
            .getOrDefault(NotifiedAlerts())
    }

    suspend fun write(state: NotifiedAlerts) = withContext(Dispatchers.IO) {
        file.writeText(NorthvaneJson.encodeToString(NotifiedAlerts.serializer(), state))
    }
}
