package app.northvane.weather.data

import android.content.Context
import app.northvane.core.json.InstantSerializer
import app.northvane.core.json.NorthvaneJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant

@Serializable
data class AiSummary(
    val locationId: String,
    val text: String,
    val model: String,
    /** The forecast download the summary was written from. */
    @Serializable(InstantSerializer::class) val forecastTime: Instant,
    @Serializable(InstantSerializer::class) val createdAt: Instant,
)

/** Last AI summary per place, so reopening the app doesn't ask the server again. */
class AiSummaryStore(context: Context) {
    private val dir = File(context.applicationContext.noBackupFilesDir, "ai")
    private fun file(id: String) = File(dir, "summary_" + id.filter { it.isLetterOrDigit() || it == '-' }.take(64) + ".json")

    suspend fun read(id: String): AiSummary? = withContext(Dispatchers.IO) {
        runCatching { NorthvaneJson.decodeFromString(AiSummary.serializer(), file(id).readText()) }.getOrNull()
    }

    suspend fun write(summary: AiSummary) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        file(summary.locationId).writeText(NorthvaneJson.encodeToString(AiSummary.serializer(), summary))
    }
}
