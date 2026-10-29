package app.northvane.weather.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.northvane.core.pollen.AndroidKeyRestriction
import java.security.MessageDigest

/**
 * The app's package name and signing-certificate SHA-1, sent with Google API
 * requests so users can restrict their key to this app in Google Cloud Console
 * ("Android apps" restriction). Neither value is secret.
 */
object AppSignature {
    @Volatile
    private var cached: AndroidKeyRestriction? = null

    fun restriction(context: Context): AndroidKeyRestriction? = cached ?: runCatching {
        val pm = context.packageManager
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return null
        val sha1 = MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
            .joinToString("") { "%02X".format(it) }
        AndroidKeyRestriction(context.packageName, sha1)
    }.getOrNull()?.also { cached = it }

    /** "AB:CD:…" form shown in Settings for pasting into Google Cloud Console. */
    fun displaySha1(context: Context): String? = restriction(context)?.certSha1?.chunked(2)?.joinToString(":")
}
