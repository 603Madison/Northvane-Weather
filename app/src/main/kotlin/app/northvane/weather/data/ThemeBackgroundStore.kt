package app.northvane.weather.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The custom theme's background photo. The picked image is decoded, scaled
 * to at most [MAX_EDGE] px and re-encoded as JPEG into app-private storage
 * (excluded from backups), so the app never keeps access to the user's
 * gallery and EXIF metadata such as GPS position is dropped.
 */
class ThemeBackgroundStore(context: Context) {
    private val app = context.applicationContext
    val file: File get() = File(app.noBackupFilesDir, "theme/background.jpg")

    suspend fun save(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@runCatching false
            val longest = maxOf(bounds.outWidth, bounds.outHeight).takeIf { it > 0 } ?: return@runCatching false
            var sample = 1
            while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
            val bitmap = app.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            val scale = MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val sized = if (scale < 1f) bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt()) else bitmap
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "background.tmp")
            tmp.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            tmp.renameTo(file)
        }.getOrDefault(false)
    }

    fun load(): Bitmap? = file.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    fun clear() {
        file.delete()
    }

    private companion object {
        const val MAX_EDGE = 1600
    }
}
