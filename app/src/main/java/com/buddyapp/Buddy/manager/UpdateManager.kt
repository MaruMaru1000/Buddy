package com.buddyapp.Buddy.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.buddyapp.Buddy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from this fork's GitHub Releases.
 *
 * - Only talks to GitHub (api.github.com and GitHub's download servers).
 * - The automatic check runs at most once a day when the app is opened, and can be
 *   switched off in Settings. "Check now" works either way.
 * - A downloaded APK is only offered if it is Buddy with a newer version. Android then
 *   checks it is signed with the same key and asks the user to confirm the install.
 */
class UpdateManager(private val context: Context) {

    data class Release(
        val version: String,
        val apkUrl: String,
        val apkSize: Long,
        val notes: String
    )

    companion object {
        const val REPO = "MaruMaru1000/Buddy"
        private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
        const val PREF_AUTO_CHECK = "auto_update_check"
        const val DEFAULT_AUTO_CHECK = true
        private const val PREF_LAST_CHECK = "last_update_check"
        private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
        private const val MAX_APK_BYTES = 200L * 1024 * 1024

        /** True if version [remote] (e.g. "1.6" or "v1.6.1") is newer than [local]. */
        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").split('.', '-', ' ')
                .map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val r = parts(remote)
            val l = parts(local)
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var autoCheck: Boolean
        get() = prefs.getBoolean(PREF_AUTO_CHECK, DEFAULT_AUTO_CHECK)
        set(value) { prefs.edit().putBoolean(PREF_AUTO_CHECK, value).apply() }

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** True when the automatic check is on and the last check was over a day ago. */
    fun shouldAutoCheck(): Boolean =
        autoCheck && System.currentTimeMillis() - prefs.getLong(PREF_LAST_CHECK, 0L) > CHECK_INTERVAL_MS

    /** The newer release, or null when Buddy is up to date (or nothing is released yet). */
    suspend fun check(): Result<Release?> = withContext(Dispatchers.IO) {
        runCatching { fetchLatest() }
    }

    private fun fetchLatest(): Release? {
        val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "BuddyApp/${BuildConfig.VERSION_NAME}")
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> return null // no releases yet
                else -> throw IOException("GitHub answered with error ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            prefs.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply()

            val version = json.optString("tag_name").removePrefix("v")
            if (version.isBlank() || !isNewer(version, currentVersion)) return null

            val assets = json.optJSONArray("assets")
            for (i in 0 until (assets?.length() ?: 0)) {
                val asset = assets!!.getJSONObject(i)
                val url = asset.optString("browser_download_url")
                if (asset.optString("name").endsWith(".apk", ignoreCase = true) &&
                    url.startsWith("https://github.com/$REPO/releases/download/")
                ) {
                    return Release(version, url, asset.optLong("size"), json.optString("body"))
                }
            }
            throw IOException("Release v$version has no APK file")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads the release APK into the app's private cache and checks it.
     * [onProgress] gets 0..1 (or -1 if the size is unknown).
     */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) { runCatching { downloadBlocking(release, onProgress) } }

    private fun downloadBlocking(release: Release, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "buddy-update.apk")

        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "BuddyApp/${BuildConfig.VERSION_NAME}")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download failed (error ${conn.responseCode})")
            }
            // GitHub redirects to its download servers; never accept a non-HTTPS hop.
            if (conn.url.protocol != "https") throw IOException("Download was not secure")
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else release.apkSize
            if (total > MAX_APK_BYTES) throw IOException("Update file is too large")

            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (done > MAX_APK_BYTES) throw IOException("Update file is too large")
                        onProgress(if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else -1f)
                    }
                }
            }
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            conn.disconnect()
        }

        // Only offer the file if it really is a newer Buddy.
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(out.path, 0)
        val installed = pm.getPackageInfo(context.packageName, 0)
        if (archive == null || archive.packageName != context.packageName) {
            out.delete()
            throw IOException("The downloaded file is not a Buddy update")
        }
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            out.delete()
            throw IOException("The downloaded file is not newer than this version")
        }
        return out
    }

    /**
     * Opens Android's installer for [apk]. If Buddy isn't allowed to install apps yet,
     * opens that settings page instead and returns false (tap Update again afterwards).
     */
    fun install(apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
