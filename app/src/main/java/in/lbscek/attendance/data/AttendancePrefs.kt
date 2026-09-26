package `in`.lbscek.attendance.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * Stores the Etlab username/password and the last fetched attendance result
 * in EncryptedSharedPreferences, so credentials never sit in plain text on
 * the device. Also stores optional per-subject name overrides -- Etlab's
 * API already gives us real subject names, but you can shorten/rename them
 * here if you want (e.g. "Analog & Digital Communication" -> "ADC").
 */
class AttendancePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "etlab_attendance_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)
    fun hasCredentials(): Boolean = getUsername() != null && getPassword() != null

    fun saveLastResult(result: AttendanceResult) {
        prefs.edit()
            .putString(KEY_LAST_RESULT, result.toJson())
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun getLastResult(): AttendanceResult? =
        prefs.getString(KEY_LAST_RESULT, null)?.let {
            runCatching { AttendanceResult.fromJson(it) }.getOrNull()
        }

    fun getLastUpdatedText(): String {
        val ts = prefs.getLong(KEY_LAST_UPDATED, 0L)
        if (ts == 0L) return "Not set up yet"
        val minutesAgo = (System.currentTimeMillis() - ts) / 60000
        return when {
            minutesAgo < 1 -> "Updated just now"
            minutesAgo < 60 -> "Updated ${minutesAgo}m ago"
            else -> "Updated ${minutesAgo / 60}h ago"
        }
    }

    /** Optional per-subject display-name override, keyed by course code. */
    fun saveSubjectNames(names: Map<String, String>) {
        val json = JSONObject()
        names.forEach { (code, name) -> json.put(code, name) }
        prefs.edit().putString(KEY_SUBJECT_NAMES, json.toString()).apply()
    }

    fun getSubjectNames(): Map<String, String> {
        val raw = prefs.getString(KEY_SUBJECT_NAMES, null) ?: return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_LAST_RESULT = "last_result"
        const val KEY_LAST_UPDATED = "last_updated"
        const val KEY_SUBJECT_NAMES = "subject_names"
        const val KEY_USE_CUSTOM_NAMES = "use_custom_names"
    }

    fun saveUseCustomNames(useCustom: Boolean) {
        prefs.edit().putBoolean(KEY_USE_CUSTOM_NAMES, useCustom).apply()
    }

    fun getUseCustomNames(): Boolean = prefs.getBoolean(KEY_USE_CUSTOM_NAMES, true)
}