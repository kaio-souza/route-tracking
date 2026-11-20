package com.distrava.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.distrava.app.data.db.ActivityEntity
import com.distrava.app.maps.MapSnapshots
import com.distrava.app.recording.TrackPoint
import com.distrava.app.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Imagem resumo do percurso, 1080 × 1920 (docs/product.md, RF05).
 * Com mapa instalado, a rota aparece sobre o mapa offline (D20); sem mapa, sobre fundo liso.
 */
class SummaryImageRenderer(private val snapshots: MapSnapshots) {

    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1920

        /** Muda quando o layout muda, para regerar imagens antigas. */
        const val VERSION = 2

        private const val MARGIN = 80f
        private const val MAP_TOP = 230
        private const val MAP_BOTTOM = 1250
        private const val MAP_PIXEL_RATIO = 2.6f
        private val ORANGE = Color.parseColor("#FC6A03")
        private val SAND = Color.parseColor("#EFECE5")
        private val GRAPHITE = Color.parseColor("#2B2B2B")
        private val MUTED = Color.parseColor("#7A766E")
        private const val MAX_POINTS = 4000
    }

    suspend fun renderToFile(activity: ActivityEntity, points: List<TrackPoint>, file: File) {
        val map = snapshots.routeMap(points, WIDTH, MAP_BOTTOM - MAP_TOP, MAP_PIXEL_RATIO)
        withContext(Dispatchers.Default) {
            val bmp = render(activity, points, map)
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            map?.recycle()
        }
    }

    private fun render(activity: ActivityEntity, points: List<TrackPoint>, map: Bitmap?): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(SAND)

        drawHeader(c, activity)
        if (map != null) {
            drawMap(c, map)
        } else {
            drawRoute(c, points, left = MARGIN + 40, top = 300f, width = WIDTH - 2 * (MARGIN + 40), height = 900f)
            val divider = Paint().apply { color = MUTED; alpha = 70; strokeWidth = 3f }
            c.drawLine(MARGIN, 1260f, WIDTH - MARGIN, 1260f, divider)
        }
        drawStats(c, activity)
        drawFooter(c)
        return bmp
    }

    private fun drawHeader(c: Canvas, activity: ActivityEntity) {
        val brand = textPaint(80f, ORANGE, Typeface.BOLD)
        c.drawText("distrava", MARGIN, 160f, brand)

        val date = textPaint(44f, GRAPHITE, Typeface.NORMAL).apply { textAlign = Paint.Align.RIGHT }
        c.drawText(Format.dateTime(activity.startedAt), WIDTH - MARGIN, 155f, date)
    }

    /** Mapa de ponta a ponta, com a rota já desenhada pelo snapshot. */
    private fun drawMap(c: Canvas, map: Bitmap) {
        val dst = Rect(0, MAP_TOP, WIDTH, MAP_BOTTOM)
        c.drawBitmap(map, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))

        // Atribuição obrigatória dos dados do OpenStreetMap.
        val text = "© OpenStreetMap"
        val p = textPaint(24f, GRAPHITE, Typeface.NORMAL).apply { alpha = 200 }
        val w = p.measureText(text)
        val bg = Paint().apply { color = Color.WHITE; alpha = 170 }
        c.drawRoundRect(RectF(WIDTH - w - 36f, MAP_BOTTOM - 46f, WIDTH - 12f, MAP_BOTTOM - 12f), 8f, 8f, bg)
        c.drawText(text, WIDTH - w - 24f, MAP_BOTTOM - 21f, p)
    }

    private fun drawRoute(c: Canvas, points: List<TrackPoint>, left: Float, top: Float, width: Float, height: Float) {
        if (points.isEmpty()) return
        val sampled = RouteProjection.downsample(points, MAX_POINTS)
        val px = RouteProjection.fit(sampled.map { it.lat to it.lon }, left, top, width, height)

        val path = Path().apply {
            moveTo(px[0].x, px[0].y)
            for (i in 1 until px.size) lineTo(px[i].x, px[i].y)
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = ORANGE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(path, line)

        val start = px.first()
        val end = px.last()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SAND }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = GRAPHITE
            strokeWidth = 8f
        }
        c.drawCircle(start.x, start.y, 18f, fill)
        c.drawCircle(start.x, start.y, 18f, stroke)
        c.drawCircle(end.x, end.y, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRAPHITE })
    }

    private fun drawStats(c: Canvas, a: ActivityEntity) {
        val col1 = MARGIN
        val col2 = WIDTH / 2f + 20f
        val row1 = 1380f
        val row2 = 1640f
        stat(c, col1, row1, "DISTÂNCIA", Format.distanceValue(a.distanceMeters), "km")
        stat(c, col2, row1, "TEMPO", Format.duration(a.movingTimeMs), null)
        stat(c, col1, row2, "VEL. MÉDIA", Format.speedValue(a.avgSpeedMps), "km/h")
        stat(c, col2, row2, "VEL. MÁX.", Format.speedValue(a.maxSpeedMps), "km/h")
    }

    private fun stat(c: Canvas, x: Float, y: Float, label: String, value: String, unit: String?) {
        val labelPaint = textPaint(36f, MUTED, Typeface.BOLD).apply { letterSpacing = 0.12f }
        c.drawText(label, x, y, labelPaint)

        val valuePaint = textPaint(104f, GRAPHITE, Typeface.BOLD)
        val valueY = y + 125f
        c.drawText(value, x, valueY, valuePaint)
        if (unit != null) {
            val unitPaint = textPaint(44f, GRAPHITE, Typeface.NORMAL)
            c.drawText(unit, x + valuePaint.measureText(value) + 14f, valueY, unitPaint)
        }
    }

    private fun drawFooter(c: Canvas) {
        val p = textPaint(32f, MUTED, Typeface.NORMAL).apply { textAlign = Paint.Align.CENTER }
        c.drawText("registrado com distrava", WIDTH / 2f, HEIGHT - 70f, p)
    }

    private fun textPaint(size: Float, color: Int, style: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, style)
    }
}
