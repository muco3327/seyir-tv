package tv.newtv.data.local

import android.content.Context
import android.content.SharedPreferences

data class IptvPerformanceSettings(
    val networkCachingMs: Int = 6000,
    val liveCachingMs: Int = 5000,
    val targetBufferSec: Int = 60,
    val maxRetries: Int = 3,
    val concurrentTestCount: Int = 15,
    val timeoutSec: Int = 8
)

object IptvSettingsManager {
    private const val PREFS_NAME = "iptv_performance_settings"
    private const val KEY_NETWORK_CACHING = "network_caching_ms"
    private const val KEY_LIVE_CACHING = "live_caching_ms"
    private const val KEY_TARGET_BUFFER = "target_buffer_sec"
    private const val KEY_MAX_RETRIES = "max_retries"
    private const val KEY_CONCURRENT_TEST = "concurrent_test_count"
    private const val KEY_TIMEOUT_SEC = "timeout_sec"
    private const val KEY_CUSTOM_SOURCES = "custom_github_sources"
    private const val KEY_CATALOG_FINGERPRINT = "live_catalog_fingerprint"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSettings(context: Context): IptvPerformanceSettings {
        val prefs = getPrefs(context)
        return IptvPerformanceSettings(
            networkCachingMs = prefs.getInt(KEY_NETWORK_CACHING, 3000),
            liveCachingMs = prefs.getInt(KEY_LIVE_CACHING, 3000),
            targetBufferSec = prefs.getInt(KEY_TARGET_BUFFER, 40),
            maxRetries = prefs.getInt(KEY_MAX_RETRIES, 3),
            concurrentTestCount = prefs.getInt(KEY_CONCURRENT_TEST, 15),
            timeoutSec = prefs.getInt(KEY_TIMEOUT_SEC, 5)
        )
    }

    fun saveSettings(context: Context, settings: IptvPerformanceSettings) {
        getPrefs(context).edit()
            .putInt(KEY_NETWORK_CACHING, settings.networkCachingMs)
            .putInt(KEY_LIVE_CACHING, settings.liveCachingMs)
            .putInt(KEY_TARGET_BUFFER, settings.targetBufferSec)
            .putInt(KEY_MAX_RETRIES, settings.maxRetries)
            .putInt(KEY_CONCURRENT_TEST, settings.concurrentTestCount)
            .putInt(KEY_TIMEOUT_SEC, settings.timeoutSec)
            .apply()
    }

    fun getCustomSources(context: Context): List<String> {
        val set = getPrefs(context).getStringSet(KEY_CUSTOM_SOURCES, emptySet()) ?: emptySet()
        return set.toList()
    }

    fun addCustomSource(context: Context, url: String) {
        if (url.isBlank()) return
        val current = getCustomSources(context).toMutableSet()
        current.add(url.trim())
        getPrefs(context).edit().putStringSet(KEY_CUSTOM_SOURCES, current).apply()
    }

    fun addCustomSources(context: Context, urls: List<String>) {
        val current = getCustomSources(context).toMutableSet()
        urls.forEach { if (it.isNotBlank()) current.add(it.trim()) }
        getPrefs(context).edit().putStringSet(KEY_CUSTOM_SOURCES, current).apply()
    }

    fun removeCustomSource(context: Context, url: String) {
        val current = getCustomSources(context).toMutableSet()
        current.remove(url.trim())
        getPrefs(context).edit().putStringSet(KEY_CUSTOM_SOURCES, current).apply()
    }

    fun clearCustomSources(context: Context) {
        getPrefs(context).edit().remove(KEY_CUSTOM_SOURCES).apply()
    }

    fun getCatalogFingerprint(context: Context): String =
        getPrefs(context).getString(KEY_CATALOG_FINGERPRINT, "").orEmpty()

    fun saveCatalogFingerprint(context: Context, fingerprint: String) {
        getPrefs(context).edit().putString(KEY_CATALOG_FINGERPRINT, fingerprint).apply()
    }
}
