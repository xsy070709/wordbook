package com.wordbook.app.update

import android.content.Context
import com.wordbook.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class GitHubUpdateChecker(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Wordbook-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseRelease(payload, BuildConfig.VERSION_NAME)?.takeUnless {
                preferences.getString(IGNORED_VERSION_KEY, null) == it.version
            }
        } finally {
            connection.disconnect()
        }
    }

    fun ignore(version: String) {
        preferences.edit().putString(IGNORED_VERSION_KEY, version).apply()
    }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/xsy070709/wordbook/releases/latest"
        private const val RELEASES_URL = "https://github.com/xsy070709/wordbook/releases"
        private const val PREFERENCES_NAME = "update_preferences"
        private const val IGNORED_VERSION_KEY = "ignored_version"

        internal fun parseRelease(json: String, currentVersion: String): AppUpdate? {
            val release = JSONObject(json)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
            val version = release.optString("tag_name").trim().removePrefix("v")
            if (!VersionComparator.isNewer(version, currentVersion)) return null
            val releaseUrl = release.optString("html_url").takeIf(::isTrustedReleaseUrl) ?: RELEASES_URL
            return AppUpdate(
                version = version,
                title = release.optString("name").trim().ifBlank { "生词本 v$version" },
                notes = release.optString("body").trim().ifBlank { "发现新版本，可前往 GitHub 查看详情。" },
                releaseUrl = releaseUrl,
            )
        }

        private fun isTrustedReleaseUrl(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.host.equals("github.com", ignoreCase = true)
        }.getOrDefault(false)
    }
}

internal object VersionComparator {
    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = parse(candidate) ?: return false
        val currentParts = parse(current) ?: return false
        val size = maxOf(candidateParts.size, currentParts.size)
        return (0 until size).firstNotNullOfOrNull { index ->
            val left = candidateParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            when {
                left > right -> true
                left < right -> false
                else -> null
            }
        } ?: false
    }

    private fun parse(value: String): List<Int>? {
        val core = value.trim().removePrefix("v").substringBefore('-')
        if (core.isBlank()) return null
        return core.split('.').map { it.toIntOrNull() ?: return null }
    }
}
