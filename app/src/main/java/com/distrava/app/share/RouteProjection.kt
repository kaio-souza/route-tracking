package com.distrava.app.share

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

data class PxPoint(val x: Float, val y: Float)

/**
 * Projeta uma rota (lat/lon) em Web Mercator e encaixa num retângulo em pixels,
 * mantendo a proporção e centralizando.
 */
object RouteProjection {
    fun fit(
        latLons: List<Pair<Double, Double>>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ): List<PxPoint> {
        if (latLons.isEmpty()) return emptyList()
        val xs = latLons.map { mercatorX(it.second) }
        val ys = latLons.map { mercatorY(it.first) }
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        val spanX = maxX - minX
        val spanY = maxY - minY
        val scale = when {
            spanX == 0.0 && spanY == 0.0 -> 0.0
            spanX == 0.0 -> height / spanY
            spanY == 0.0 -> width / spanX
            else -> minOf(width / spanX, height / spanY)
        }
        val offsetX = left + (width - spanX * scale) / 2
        val offsetY = top + (height - spanY * scale) / 2
        return xs.indices.map { i ->
            PxPoint(
                x = (offsetX + (xs[i] - minX) * scale).toFloat(),
                // Y do Mercator cresce para o norte; na tela cresce para baixo.
                y = (offsetY + (maxY - ys[i]) * scale).toFloat(),
            )
        }
    }

    /** Reduz a quantidade de pontos mantendo o primeiro e o último. */
    fun <T> downsample(points: List<T>, max: Int): List<T> {
        if (points.size <= max || max < 2) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return List(max) { i -> points[(i * step).toInt().coerceAtMost(points.size - 1)] }
    }

    private fun mercatorX(lon: Double) = (lon + 180.0) / 360.0
    private fun mercatorY(lat: Double): Double {
        val rad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return ln(tan(PI / 4 + rad / 2)) / (2 * PI)
    }
}
