package `in`.lbscek.attendance.widget

import android.content.Context
import android.content.Intent
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import `in`.lbscek.attendance.data.AttendancePrefs
import `in`.lbscek.attendance.data.AttendanceResult
import `in`.lbscek.attendance.ui.MainActivity

class TotalPercentageWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = AttendancePrefs(context)
        val hasCredentials = prefs.hasCredentials()
        val result = if (hasCredentials) prefs.getLastResult() else null

        provideContent {
            GlanceTheme {
                WidgetContent(hasCredentials, result)
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun WidgetContent(
        hasCredentials: Boolean,
        result: AttendanceResult?
    ) {
        val context = LocalContext.current
        val clickAction = actionStartActivity(Intent(context, MainActivity::class.java))

        // Google Sans Flex, instanced at a high value on its ROND
        // (roundness) axis — the actual typeface family M3 Expressive's
        // "Rounded" style is built from. See google_sans_flex_rounded.xml.
        val roundedFont = FontFamily("google_sans_flex_rounded")

        // Rasterize the real Material 3 Expressive clamshell polygon
        // instead of an approximated vector drawable. Baked at a fixed,
        // density-scaled resolution so it stays crisp once RemoteViews
        // stretches it to the widget's actual cell size.
        val density = context.resources.displayMetrics.density
        val rasterSizePx = (220 * density).toInt()
        val surfaceColorArgb = GlanceTheme.colors.surface.getColor(context).toArgb()

        val clamShellBitmap = remember(surfaceColorArgb, rasterSizePx) {
            rasterizeMaterialShape(
                shape = MaterialShapes.ClamShell,
                widthPx = rasterSizePx,
                heightPx = rasterSizePx,
                colorArgb = surfaceColorArgb
            )
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(imageProvider = ImageProvider(clamShellBitmap))
                // Slightly increased padding so text clears the clamshell's
                // angled/curved edges.
                .padding(24.dp)
                .clickable(clickAction),
            contentAlignment = Alignment.Center
        ) {
            if (!hasCredentials || result == null) {
                Text(
                    text = "Tap to\nset up",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontFamily = roundedFont,
                        fontWeight = FontWeight.Bold
                    )
                )
            } else {
                val percent = result.overallPercent
                val isGood = percent >= 75.0
                val statusLabel = if (isGood) "On Track" else "Low"

                val badgeBg = if (isGood) GlanceTheme.colors.primaryContainer
                else GlanceTheme.colors.errorContainer
                val badgeFg = if (isGood) GlanceTheme.colors.onPrimaryContainer
                else GlanceTheme.colors.onErrorContainer

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ATTENDANCE",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontFamily = roundedFont,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    Text(
                        text = "${percent.toInt()}%",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 36.sp,
                            fontFamily = roundedFont,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    Box(
                        modifier = GlanceModifier
                            .background(badgeBg)
                            .cornerRadius(100.dp)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusLabel,
                            style = TextStyle(
                                color = badgeFg,
                                fontSize = 10.sp,
                                fontFamily = roundedFont,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}