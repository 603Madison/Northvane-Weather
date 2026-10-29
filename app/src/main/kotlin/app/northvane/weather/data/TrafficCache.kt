package app.northvane.weather.data

import android.content.Context
import app.northvane.core.json.NorthvaneJson
import app.northvane.core.traffic.TrafficSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant

/** Last traffic events per place, so the tab can show (or hide) them when traffic can't be downloaded. */
class TrafficCache(context: Context) {
    private val dir = File(context.applicationContext.noBackupFilesDir, "traffic")
    private fun file(id: String) = File(dir, "traffic_" + id.filter { it.isLetterOrDigit() || it == '-' }.take(64) + ".json")

    suspend fun read(id: String): TrafficSnapshot? = withContext(Dispatchers.IO) {
        runCatching { NorthvaneJson.decodeFromString(TrafficSnapshot.serializer(), file(id).readText()) }.getOrNull()
    }

    suspend fun write(id: String, snapshot: TrafficSnapshot) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        file(id).writeText(NorthvaneJson.encodeToString(TrafficSnapshot.serializer(), snapshot))
    }

    /** Age of the cached events for a place, or null if there are none. */
    fun age(id: String, now: Instant = Instant.now()): Duration? =
        file(id).takeIf { it.exists() }?.let { Duration.between(Instant.ofEpochMilli(it.lastModified()), now) }
}
