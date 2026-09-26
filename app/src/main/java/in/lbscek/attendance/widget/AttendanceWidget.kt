package `in`.lbscek.attendance.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import `in`.lbscek.attendance.R
import `in`.lbscek.attendance.data.AttendancePrefs
import `in`.lbscek.attendance.data.AttendanceResult
import `in`.lbscek.attendance.data.SubjectAttendance
import `in`.lbscek.attendance.ui.MainActivity

class AttendanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = AttendancePrefs(context)
        val result = prefs.getLastResult()
        val updatedText = prefs.getLastUpdatedText()
        val hasCredentials = prefs.hasCredentials()
        val subjectNameOverrides = prefs.getSubjectNames()

        provideContent {
            GlanceTheme {
                WidgetContent(hasCredentials, result, updatedText, subjectNameOverrides)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        hasCredentials: Boolean,
        result: AttendanceResult?,
        updatedText: String,
        subjectNameOverrides: Map<String, String>
    ) {
        val context = LocalContext.current

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(24.dp)
                .padding(8.dp)
        ) {
            if (!hasCredentials || result == null) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Tap to set up",
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                    )
                }
            } else {
                Row(modifier = GlanceModifier.fillMaxSize()) {
                    SummaryCard(
                        overallPercent = result.overallPercent,
                        updatedText = updatedText,
                        modifier = GlanceModifier
                            .width(84.dp)
                            .fillMaxHeight()
                            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                    )

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Column instead of LazyColumn: LazyColumn is inherently
                    // scrollable, and we want the 4 rows to divide the
                    // available height evenly so they always fit.
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                    ) {
                        val rows = result.subjects.chunked(2)
                        rows.forEachIndexed { index, pair ->
                            val isTopRow = (index == 0)
                            val isBottomRow = (index == rows.size - 1)

                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .defaultWeight()
                                    .padding(bottom = if (index < rows.size - 1) 7.dp else 0.dp)
                            ) {
                                if (pair.size == 2) {
                                    SubjectCard(
                                        subject = pair[0],
                                        label = labelFor(pair[0], subjectNameOverrides),
                                        context = context,
                                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                        isTopRight = false,
                                        isBottomRight = false
                                    )
                                    Spacer(modifier = GlanceModifier.width(7.dp))
                                    SubjectCard(
                                        subject = pair[1],
                                        label = labelFor(pair[1], subjectNameOverrides),
                                        context = context,
                                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                        isTopRight = isTopRow,
                                        isBottomRight = isBottomRow
                                    )
                                } else {
                                    // Odd one out: let it take the whole row width.
                                    SubjectCard(
                                        subject = pair[0],
                                        label = labelFor(pair[0], subjectNameOverrides),
                                        context = context,
                                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                        isTopRight = isTopRow,
                                        isBottomRight = isBottomRow
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun labelFor(subject: SubjectAttendance, overrides: Map<String, String>): String {
        overrides[subject.code]?.takeIf { it.isNotBlank() }?.let { return it }
        if (subject.name.isNotBlank()) return subject.name
        return subject.code
    }

    @Composable
    private fun SummaryCard(
        overallPercent: Double,
        updatedText: String,
        modifier: GlanceModifier
    ) {
        val statusLabel = if (overallPercent >= 75.0) "On Track" else "Low"
        val labelColor = GlanceTheme.colors.primaryContainer.getColor(LocalContext.current)
        val labelColorProvider = GlanceTheme.colors.primaryContainer
        // val labelColor = GlanceTheme.colors.onPrimaryContainer.getColor(LocalContext.current)

        Box(
            modifier = modifier
                .background(
                    imageProvider = ImageProvider(R.drawable.bg_card_summary),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    // colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer)
                )
                .padding(10.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    RotatedText(
                        text = "SUMMARY",
                        fontSize = 12.sp,
                        color = labelColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    RotatedText(
                        text = "ATTENDANCE",
                        fontSize = 12.sp,
                        color = labelColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.0f".format(overallPercent),
                        style = TextStyle(
                            color = labelColorProvider,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                    Text(
                        "%",
                        style = TextStyle(
                            color = labelColorProvider,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }
                Text(
                    statusLabel,
                    style = TextStyle(color = labelColorProvider, fontSize = 10.sp)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    updatedText,
                    style = TextStyle(color = labelColorProvider, fontSize = 7.sp)
                )
            }
        }
    }

    /**
     * Glance's [Text] has no rotation support, so we rasterize the string to a
     * bitmap and rotate it 90 degrees. The resulting Image occupies swapped
     * width/height dimensions compared to the upright text.
     */
    @Composable
    private fun RotatedText(
        text: String,
        fontSize: TextUnit,
        color: Color,
        fontWeight: FontWeight = FontWeight.Normal,
    ) {
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        val scaledDensity = context.resources.displayMetrics.scaledDensity

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textSize = fontSize.value * scaledDensity
            typeface = if (fontWeight == FontWeight.Bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        val textWidth = paint.measureText(text).toInt().coerceAtLeast(1)
        val fm = paint.fontMetrics
        val textHeight = (fm.descent - fm.ascent).toInt().coerceAtLeast(1)

        val upright = Bitmap.createBitmap(textWidth, textHeight, Bitmap.Config.ARGB_8888)
        Canvas(upright).drawText(text, 0f, -fm.ascent, paint)

        val matrix = Matrix().apply { postRotate(-90f) }
        val rotated = Bitmap.createBitmap(upright, 0, 0, textWidth, textHeight, matrix, true)
        upright.recycle()

        val widthDp: Dp = (textHeight / density).dp
        val heightDp: Dp = (textWidth / density).dp

        Image(
            provider = ImageProvider(rotated),
            contentDescription = text,
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.size(widthDp, heightDp)
        )
    }

    @Composable
    private fun SubjectCard(
        subject: SubjectAttendance,
        label: String,
        context: Context,
        modifier: GlanceModifier,
        isTopRight: Boolean = false,
        isBottomRight: Boolean = false
    ) {
        val backgroundDrawable = when {
            isTopRight && isBottomRight -> R.drawable.bg_card_outer_top_bottom_right
            isTopRight -> R.drawable.bg_card_outer_top_right
            isBottomRight -> R.drawable.bg_card_outer_bottom_right
            else -> R.drawable.bg_card_inner
        }
        val background = when {
            subject.percent >= 85.0 -> GlanceTheme.colors.tertiaryContainer
            subject.percent >= 65.0 -> GlanceTheme.colors.secondaryContainer
            else -> GlanceTheme.colors.errorContainer
        }
        val onBackground = when {
            subject.percent >= 85.0 -> GlanceTheme.colors.onTertiaryContainer
            subject.percent >= 65.0 -> GlanceTheme.colors.onSecondaryContainer
            else -> GlanceTheme.colors.onErrorContainer
        }

        Box(
            modifier = modifier
                .background(
                    imageProvider = ImageProvider(backgroundDrawable),
                    colorFilter = ColorFilter.tint(background)
                )
                .padding(horizontal = 10.dp)
                .clickable(
                    actionStartActivity(
                        Intent(context, MainActivity::class.java)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        subject.code,
                        style = TextStyle(color = onBackground, fontSize = 8.sp)
                    )
                    Text(
                        label,
                        style = TextStyle(
                            color = onBackground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    "%.0f".format(subject.percent),
                    style = TextStyle(
                        color = onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic
                    )
                )
            }
        }
    }
}