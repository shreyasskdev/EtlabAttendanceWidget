package `in`.lbscek.attendance.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.background
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import `in`.lbscek.attendance.data.AttendancePrefs
import `in`.lbscek.attendance.data.AttendanceResult
import `in`.lbscek.attendance.data.SubjectAttendance
import `in`.lbscek.attendance.ui.MainActivity
import `in`.lbscek.attendance.work.AttendanceWorker

class AttendanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = AttendancePrefs(context)
        val result = prefs.getLastResult()
        val updatedText = prefs.getLastUpdatedText()
        val hasCredentials = prefs.hasCredentials()
        val subjectNames = prefs.getSubjectNames()
        val showCustomNames = prefs.getShowCustomNames()

        provideContent {
            GlanceTheme {
                WidgetContent(hasCredentials, result, updatedText, subjectNames, showCustomNames)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        hasCredentials: Boolean,
        result: AttendanceResult?,
        updatedText: String,
        subjectNames: Map<String, String>,
        showCustomNames: Boolean
    ) {
        val context = LocalContext.current

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF15161B))
                .padding(12.dp)
        ) {
            if (!hasCredentials || result == null) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                ) {
                    Text(
                        "Tap to set up",
                        style = TextStyle(color = ColorProvider(Color.LightGray), fontSize = 12.sp)
                    )
                }
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // Header: overall % + refresh, tappable to open the app
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            val color = if (result.overallPercent < 75.0) Color(0xFFFF7A7A) else Color(0xFF7CFFA0)
                            Text(
                                "%.1f%%".format(result.overallPercent),
                                style = TextStyle(color = ColorProvider(color), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "${result.totalPresent}/${result.totalHours} hrs \u2022 $updatedText",
                                style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 9.sp)
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        "Refresh now",
                        modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                        style = TextStyle(color = ColorProvider(Color(0xFF8AB4FF)), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Subject-wise breakdown
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                        items(result.subjects) { subject ->
                            val label = if (showCustomNames) {
                                subjectNames[subject.code]?.takeIf { it.isNotBlank() } ?: subject.code
                            } else {
                                subject.code
                            }
                            SubjectRow(label, subject)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SubjectRow(label: String, subject: SubjectAttendance) {
        val color = if (subject.percent < 75.0) Color(0xFFFF7A7A) else Color(0xFFE0E0E0)
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            Text(
                label,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp)
            )
            Text(
                "${subject.present}/${subject.total}",
                modifier = GlanceModifier.width(46.dp),
                style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 10.sp)
            )
            Text(
                "%.0f%%".format(subject.percent),
                modifier = GlanceModifier.width(38.dp),
                style = TextStyle(color = ColorProvider(color), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AttendanceWorker.enqueueOneTime(context)
    }
}