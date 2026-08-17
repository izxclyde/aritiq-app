package com.aritiq.calcnote.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aritiq.calcnote.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/izxclyde/aritiq-app/releases/latest"

@Serializable
private data class Release(val tag_name: String = "", val assets: List<Asset> = emptyList())

@Serializable
private data class Asset(val browser_download_url: String = "")

actual fun checkForUpdate(): UpdateInfo? {
    return try {
        val conn = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "aritiq-app")
            if (conn.responseCode != 200) return null
            val release = Json { ignoreUnknownKeys = true }
                .decodeFromString<Release>(conn.inputStream.bufferedReader().use { it.readText() })
            val versionCode = release.tag_name.substringAfterLast('-').toIntOrNull() ?: return null
            val apkUrl = release.assets.firstOrNull { it.browser_download_url.endsWith(".apk") }?.browser_download_url
                ?: return null
            if (versionCode <= BuildConfig.VERSION_CODE) null else UpdateInfo(versionCode, apkUrl)
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}

actual fun downloadAndInstallUpdate(context: Any, update: UpdateInfo): Boolean {
    return try {
        val ctx = context as Context
        val file = File(ctx.cacheDir, "aritiq-update.apk")
        val conn = URL(update.downloadUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            conn.disconnect()
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

actual fun canRequestPackageInstalls(context: Any): Boolean {
    val ctx = context as Context
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ctx.packageManager.canRequestPackageInstalls()
    } else {
        true
    }
}

actual fun openUnknownAppSources(context: Any) {
    val ctx = context as Context
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${ctx.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}