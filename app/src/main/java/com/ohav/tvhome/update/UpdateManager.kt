package com.ohav.tvhome.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ohav.tvhome.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** [dmUrl]: the build's baseline profile as dex metadata, so the update is compiled ahead of time on install. */
data class Release(val versionName: String, val versionCode: Int, val apkUrl: String, val size: Long, val dmUrl: String?)

data class Download(val apk: File, val dm: File?)

/** Self-update from the latest GitHub Release of this repo (tag v1.0.<versionCode>). */
object UpdateManager {
    private val api = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val conn = (URL(api).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "TVHome/${BuildConfig.VERSION_NAME}")
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != 200) error("GitHub ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: error("no APK in release")
            val dm = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".dm") }
            Release(
                versionName = tag,
                versionCode = tag.substringAfterLast('.').toIntOrNull() ?: 0,
                apkUrl = apk.getString("browser_download_url"),
                size = apk.optLong("size"),
                dmUrl = dm?.getString("browser_download_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    fun isNewer(r: Release) = r.versionCode > BuildConfig.VERSION_CODE

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** Opens the "install unknown apps" switch for this app (falls back to security settings). */
    fun requestInstallPermission(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            } catch (_: Exception) {
            }
        }
    }

    suspend fun download(context: Context, r: Release, onProgress: (Int) -> Unit): Download = withContext(Dispatchers.IO) {
        val apk = fetch(r.apkUrl, File(context.cacheDir, "update.apk"), r.size, onProgress)
        // Without it the update runs uncompiled until the system's overnight compile: skip it rather than fail.
        val dm = r.dmUrl?.let { url -> runCatching { fetch(url, File(context.cacheDir, "update.dm"), 0) { } }.getOrNull() }
        Download(apk, dm)
    }

    private suspend fun fetch(url: String, out: File, size: Long, onProgress: (Int) -> Unit): File {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (conn.responseCode !in 200..299) error("download ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: size
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != last) { last = pct; withContext(Dispatchers.Main) { onProgress(pct) } }
                    }
                }
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    fun install(context: Context, download: Download) {
        val (apk, dm) = download
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            if (dm != null) session.openWrite("base.dm", 0, dm.length()).use { out ->
                dm.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val pending = PendingIntent.getBroadcast(
                context, id,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }
}
