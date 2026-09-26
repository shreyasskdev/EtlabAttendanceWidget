package `in`.lbscek.attendance.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class TotalPercentageWidgetReceiver : GlanceAppWidgetReceiver() {
    // This line references the class, fixing the "never used" warning
    // and telling Android how to load the widget.
    override val glanceAppWidget: GlanceAppWidget = TotalPercentageWidget()
}