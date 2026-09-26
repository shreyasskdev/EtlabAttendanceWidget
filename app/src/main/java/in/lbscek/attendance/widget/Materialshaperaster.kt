package `in`.lbscek.attendance.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.graphics.shapes.RoundedPolygon

/**
 * Glance widgets render through RemoteViews, not real Compose, so a Compose
 * `Shape` (like the ones exposed by `MaterialShapes`) can't be applied with
 * `Modifier.clip()` the way it could in a normal Activity/Fragment. Glance's
 * `background()` only accepts a Color or an `ImageProvider` (a drawable
 * resource or a Bitmap).
 *
 * So instead of approximating the shape as a hand-drawn vector drawable, we
 * rasterize the *actual* Material 3 Expressive polygon into a Bitmap once
 * per composition — the same trick this widget already uses to rotate text
 * in `AttendanceWidget.RotatedText`.
 */

/**
 * Converts a [RoundedPolygon]'s cubic outline into an android.graphics.Path.
 * This is the standard conversion Google uses across its Material 3
 * Expressive shape samples/codelabs.
 */
fun RoundedPolygon.toAndroidPath(path: Path = Path()): Path {
    path.rewind()
    val cubics = this.cubics
    if (cubics.isEmpty()) return path

    val first = cubics.first()
    path.moveTo(first.anchor0X, first.anchor0Y)
    for (cubic in cubics) {
        path.cubicTo(
            cubic.control0X, cubic.control0Y,
            cubic.control1X, cubic.control1Y,
            cubic.anchor1X, cubic.anchor1Y
        )
    }
    path.close()
    return path
}

/**
 * Rasterizes a [RoundedPolygon] (e.g. `MaterialShapes.ClamShell`) as a
 * solid-fill Bitmap, scaled to exactly [widthPx] x [heightPx].
 *
 * RoundedPolygon coordinates are centered on the origin and aren't
 * guaranteed to span a clean 0..1 box, so we measure the polygon's real
 * bounds via [RoundedPolygon.calculateBounds] and map them onto our target
 * pixel rectangle, rather than assuming a fixed coordinate range.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun rasterizeMaterialShape(
    shape: RoundedPolygon,
    widthPx: Int,
    heightPx: Int,
    colorArgb: Int
): Bitmap {
    val safeWidth = widthPx.coerceAtLeast(1)
    val safeHeight = heightPx.coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }

    val path = shape.toAndroidPath()

    val bounds = shape.calculateBounds()
    val left = bounds[0]
    val top = bounds[1]
    val right = bounds[2]
    val bottom = bounds[3]
    val shapeWidth = (right - left).coerceAtLeast(0.0001f)
    val shapeHeight = (bottom - top).coerceAtLeast(0.0001f)

    val matrix = Matrix().apply {
        // Move the polygon's top-left corner to the origin, then scale it
        // to exactly fill the bitmap's pixel dimensions.
        postTranslate(-left, -top)
        postScale(safeWidth / shapeWidth, safeHeight / shapeHeight)
    }
    path.transform(matrix)

    canvas.drawPath(path, paint)
    return bitmap
}