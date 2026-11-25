package com.buddyapp.Buddy.manager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.security.MessageDigest

data class KeyUsageStats(
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val lastUsedTimestamp: Long = 0L
) {
    val errorRate: Float
        get() = if (totalRequests > 0) (failedRequests.toFloat() / totalRequests) * 100f else 0f
}

/**
 * Per-key request counters.
 * Entries are stored under a SHA-256 hash of the API key, never the key itself.
 */
class UsageManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("api_usage_prefs", Context.MODE_PRIVATE)

    private companion object {
        const val ENTRY_PREFIX = "sha256:"
    }

    init {
        migrateRawKeyEntries()
    }

    /** Older versions used the raw API key as the entry name. Move those to hashed names. */
    private fun migrateRawKeyEntries() {
        val rawEntries = prefs.all.filterKeys { !it.startsWith(ENTRY_PREFIX) }
        if (rawEntries.isEmpty()) return
        val editor = prefs.edit()
        for ((rawKey, value) in rawEntries) {
            val hashed = entryName(rawKey)
            if (value is String && !prefs.contains(hashed)) editor.putString(hashed, value)
            editor.remove(rawKey)
        }
        editor.commit()
    }

    private fun entryName(apiKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(apiKey.toByteArray(Charsets.UTF_8))
        return ENTRY_PREFIX + digest.joinToString("") { "%02x".format(it) }
    }

    fun recordRequest(key: String, success: Boolean) {
        val stats = getStats(key)
        val newTotal = stats.totalRequests + 1
        val newSuccess = if (success) stats.successfulRequests + 1 else stats.successfulRequests
        val newFailed = if (!success) stats.failedRequests + 1 else stats.failedRequests

        val obj = JSONObject()
        obj.put("total", newTotal)
        obj.put("success", newSuccess)
        obj.put("failed", newFailed)
        obj.put("lastUsed", System.currentTimeMillis())

        prefs.edit().putString(entryName(key), obj.toString()).apply()
    }

    fun getStats(key: String): KeyUsageStats {
        val jsonStr = prefs.getString(entryName(key), null) ?: return KeyUsageStats()
        return try {
            val obj = JSONObject(jsonStr)
            KeyUsageStats(
                totalRequests = obj.optInt("total", 0),
                successfulRequests = obj.optInt("success", 0),
                failedRequests = obj.optInt("failed", 0),
                lastUsedTimestamp = obj.optLong("lastUsed", 0L)
            )
        } catch (e: Exception) {
            KeyUsageStats()
        }
    }

    fun deleteStats(key: String) {
        prefs.edit().remove(entryName(key)).apply()
    }
}
