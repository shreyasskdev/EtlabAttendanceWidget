package `in`.lbscek.attendance.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Talks to Etlab's own mobile-app JSON API (the same one the official
 * Android app uses), at [ETLAB_BASE_URL]. This is a *much* more reliable
 * foundation than scraping the web HTML: it's the real backend contract,
 * gives full subject names directly, and needs no session cookies -- just
 * a bearer token from /app/login.
 *
 * Endpoints reverse-engineered from the Etlab Android APK by the
 * open-source `retlab` project (github.com/dcdunkan/retlab /
 * github.com/dcdunkan/retlab-generate).
 */
class EtlabRepository(private val baseClient: OkHttpClient = OkHttpClient()) {

    data class FetchResult(val attendance: AttendanceResult)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchAttendance(username: String, password: String): FetchResult =
        withContext(Dispatchers.IO) {
            val accessToken = login(username, password)
            val attendance = fetchAttendanceBySubject(accessToken)
            FetchResult(attendance)
        }

    private fun login(username: String, password: String): String {
        val payload = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val request = Request.Builder()
            .url("$ETLAB_BASE_URL/app/login")
            .header("User-Agent", ETLAB_USER_AGENT)
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        baseClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(bodyText) }.getOrNull()
                ?: throw ParsingException("Etlab returned an unexpected login response.")

            val loggedIn = json.optBoolean("login", false)
            val accessToken = json.optString("access_token", "")

            if (!loggedIn || accessToken.isBlank()) {
                val serverError = json.optString("error", "").takeIf { it.isNotBlank() }
                if (serverError != null) throw ParsingException(serverError)
                throw InvalidCredentialsException()
            }

            return accessToken
        }
    }

    private fun fetchAttendanceBySubject(accessToken: String): AttendanceResult {
        val payload = JSONObject().put("sem_id", "").toString()

        val request = Request.Builder()
            .url("$ETLAB_BASE_URL/app/attendancebysubject")
            .header("User-Agent", ETLAB_USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        baseClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(bodyText) }.getOrNull()
                ?: throw ParsingException("Etlab returned an unexpected attendance response.")

            if (!json.optBoolean("login", true)) {
                throw SessionExpiredException()
            }

            val subjectsJson = json.optJSONArray("subjects")
                ?: throw ParsingException("No subjects found in Etlab's response.")

            val subjects = mutableListOf<SubjectAttendance>()
            var sumPresent = 0
            var sumTotal = 0

            for (i in 0 until subjectsJson.length()) {
                val s = subjectsJson.optJSONObject(i) ?: continue
                val code = s.optString("code", "").trim().uppercase()
                val name = s.optString("subject", "").trim()
                if (code.isBlank()) continue

                // "total_subject" is formatted "present/total", e.g. "23/24".
                val raw = s.optString("total_subject", "").trim()
                val parts = raw.split("/")
                val present = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
                val total = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0

                val percent = s.optString("percentage_subject", "")
                    .replace("%", "")
                    .trim()
                    .toDoubleOrNull()
                    ?: if (total > 0) (present * 100.0 / total) else 0.0

                subjects.add(SubjectAttendance(code = code, name = name, present = present, total = total, percent = percent))
                sumPresent += present
                sumTotal += total
            }

            val overallPercent = json.optString("total_percent", "")
                .replace("%", "")
                .trim()
                .toDoubleOrNull()
                ?: if (sumTotal > 0) (sumPresent * 100.0 / sumTotal) else 0.0

            return AttendanceResult(
                subjects = subjects,
                totalPresent = sumPresent,
                totalHours = sumTotal,
                overallPercent = overallPercent,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}