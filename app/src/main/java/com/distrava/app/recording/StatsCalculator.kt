package com.distrava.app.recording

data class RideStats(
    val distanceMeters: Double,
    val totalTimeMs: Long,
    val movingTimeMs: Long,
    val avgSpeedMps: Double,
    val maxSpeedMps: Double,
)

/**
 * Estatísticas do percurso (docs/product.md, RF04).
 * Os pontos recebidos já devem ter passado pelo [LocationFilter].
 */
object StatsCalculator {
    /** Abaixo disso o ciclista é considerado parado. */
    const val MOVING_THRESHOLD_MPS = 1.0 / 3.6

    /** Quantidade de trechos na média móvel da velocidade máxima. */
    const val MAX_SPEED_WINDOW = 5

    /** Percurso com menos que isso é descartado (RF03). */
    const val MIN_DISTANCE_M = 50.0

    fun compute(points: List<TrackPoint>, startedAt: Long, finishedAt: Long): RideStats {
        var distance = 0.0
        var movingMs = 0L
        val segDist = DoubleArray(maxOf(points.size - 1, 0))
        val segMs = LongArray(maxOf(points.size - 1, 0))

        for (i in 1 until points.size) {
            val d = Geo.distanceMeters(points[i - 1], points[i])
            val dt = points[i].time - points[i - 1].time
            segDist[i - 1] = d
            segMs[i - 1] = dt
            distance += d
            if (dt > 0 && d / (dt / 1000.0) > MOVING_THRESHOLD_MPS) movingMs += dt
        }

        var maxSpeed = 0.0
        if (segDist.isNotEmpty()) {
            val window = minOf(MAX_SPEED_WINDOW, segDist.size)
            for (end in window - 1 until segDist.size) {
                var d = 0.0
                var t = 0L
                for (j in end - window + 1..end) {
                    d += segDist[j]
                    t += segMs[j]
                }
                if (t > 0) maxSpeed = maxOf(maxSpeed, d / (t / 1000.0))
            }
        }

        val avg = if (movingMs > 0) distance / (movingMs / 1000.0) else 0.0
        return RideStats(
            distanceMeters = distance,
            totalTimeMs = (finishedAt - startedAt).coerceAtLeast(0),
            movingTimeMs = movingMs,
            avgSpeedMps = avg,
            maxSpeedMps = maxSpeed,
        )
    }

    fun isValidRide(points: List<TrackPoint>, stats: RideStats): Boolean =
        points.size >= 2 && stats.distanceMeters >= MIN_DISTANCE_M
}
