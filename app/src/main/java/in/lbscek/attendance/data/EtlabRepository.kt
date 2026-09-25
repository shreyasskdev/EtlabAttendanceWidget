package `in`.lbscek.attendance.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Talks to an Etlab instance (the campus-management portal used by LBSCEK
 * and many other KTU-affiliated colleges, at [ETLAB_BASE_URL]).
 *
 * The login + scraping flow mirrors the one used by the open-source
 * rit-etlab-api project (github.com/devadathanmb/rit-etlab-api), adapted to
 * run natively on-device instead of through a hosted backend.
 */
class EtlabRepository(private val baseClient: OkHttpClient = OkHttpClient()) {

    suspend fun fetchAttendance(
        username: String,
        password: String,
        semester: Int
    ): AttendanceResult = withContext(Dispatchers.IO) {
        val cookieJar = SimpleCookieJar()
        val client = baseClient.newBuilder().cookieJar(cookieJar).build()

        login(client, username, password)
        val doc = loadAttendancePage(client, semester)
        parseAttendance(doc)
    }

    private fun login(client: OkHttpClient, username: String, password: String) {
        val body = FormBody.Builder()
            .add("LoginForm[username]", username)
            .add("LoginForm[password]", password)
            .add("yt0", "")
            .build()

        val request = Request.Builder()
            .url("$ETLAB_BASE_URL/user/login")
            .header("User-Agent", ETLAB_USER_AGENT)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val html = response.body?.string().orEmpty()
            val title = Jsoup.parse(html).title()
            // Etlab redirects back to a page titled "... | login" on failure.
            if (title.contains("login", ignoreCase = true)) {
                throw InvalidCredentialsException()
            }
        }
    }

    private fun loadAttendancePage(client: OkHttpClient, semester: Int): Document {
        val request = Request.Builder()
            .url("$ETLAB_BASE_URL/ktuacademics/student/viewattendancesubject/$semester")
            .header("User-Agent", ETLAB_USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html)
            if (doc.title().contains("login", ignoreCase = true)) {
                throw SessionExpiredException()
            }
            return doc
        }
    }

    private fun parseAttendance(doc: Document): AttendanceResult {
        val table = doc.selectFirst("table.items")
            ?: throw ParsingException(
                "Couldn't find the attendance table. Etlab's page layout may " +
                    "have changed, or this semester has no attendance data yet."
            )

        val headers = table.select("th")
        val cells = table.select("td")

        if (cells.size < 5) {
            throw ParsingException("Attendance table looked empty.")
        }

        // Layout: [reg_no, roll_no, name, subject1, subject2, ..., total, overall%]
        val subjects = mutableListOf<SubjectAttendance>()
        for (i in 3 until cells.size - 2) {
            val code = headers.getOrNull(i)?.text()?.trim().orEmpty()
            val raw = cells[i].text().trim()
            val slashParts = raw.split("/")
            if (slashParts.size < 2 || code.isBlank()) continue

            val present = slashParts[0].trim().toIntOrNull() ?: continue
            val rest = slashParts[1]
            val total = rest.substringBefore("(").trim().toIntOrNull() ?: continue
            val percent = Regex("""\(([\d.]+)""").find(rest)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: if (total > 0) (present * 100.0 / total) else 0.0

            subjects.add(SubjectAttendance(code, present, total, percent))
        }

        val totalCell = cells[cells.size - 2].text().trim()
        val totalParts = totalCell.split("/")
        val totalPresent = totalParts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val totalHours = totalParts.getOrNull(1)?.trim()?.substringBefore("(")?.trim()?.toIntOrNull() ?: 0

        val overallText = cells[cells.size - 1].text().trim().replace("%", "")
        val overallPercent = overallText.toDoubleOrNull()
            ?: if (totalHours > 0) (totalPresent * 100.0 / totalHours) else 0.0

        return AttendanceResult(
            subjects = subjects,
            totalPresent = totalPresent,
            totalHours = totalHours,
            overallPercent = overallPercent,
            updatedAt = System.currentTimeMillis()
        )
    }
}
