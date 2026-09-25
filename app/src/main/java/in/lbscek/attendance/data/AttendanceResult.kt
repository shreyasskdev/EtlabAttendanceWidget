package `in`.lbscek.attendance.data

import org.json.JSONArray
import org.json.JSONObject

data class SubjectAttendance(
    val code: String,
    val present: Int,
    val total: Int,
    val percent: Double
)

data class AttendanceResult(
    val subjects: List<SubjectAttendance>,
    val totalPresent: Int,
    val totalHours: Int,
    val overallPercent: Double,
    val updatedAt: Long
) {
    fun toJson(): String {
        val root = JSONObject()
        val arr = JSONArray()
        subjects.forEach { s ->
            val o = JSONObject()
            o.put("code", s.code)
            o.put("present", s.present)
            o.put("total", s.total)
            o.put("percent", s.percent)
            arr.put(o)
        }
        root.put("subjects", arr)
        root.put("totalPresent", totalPresent)
        root.put("totalHours", totalHours)
        root.put("overallPercent", overallPercent)
        root.put("updatedAt", updatedAt)
        return root.toString()
    }

    companion object {
        fun fromJson(json: String): AttendanceResult {
            val root = JSONObject(json)
            val arr = root.getJSONArray("subjects")
            val subjects = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SubjectAttendance(
                    code = o.getString("code"),
                    present = o.getInt("present"),
                    total = o.getInt("total"),
                    percent = o.getDouble("percent")
                )
            }
            return AttendanceResult(
                subjects = subjects,
                totalPresent = root.getInt("totalPresent"),
                totalHours = root.getInt("totalHours"),
                overallPercent = root.getDouble("overallPercent"),
                updatedAt = root.getLong("updatedAt")
            )
        }
    }
}

class InvalidCredentialsException : Exception("Invalid username or password")
class SessionExpiredException : Exception("Etlab session expired")
class ParsingException(message: String) : Exception(message)
