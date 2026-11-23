package com.distrava.app.recording

/**
 * Perfil de altitude do percurso.
 *
 * @param samples pares (distância acumulada em m, altitude suavizada em m), reduzidos para o gráfico.
 */
data class ElevationProfile(
    val gainM: Double,
    val lossM: Double,
    val minM: Double,
    val maxM: Double,
    val samples: List<Pair<Double, Double>>,
)

/**
 * Altimetria a partir da altitude do GPS.
 *
 * A altitude do GPS oscila alguns metros mesmo parado. Para o ganho não sair inflado:
 * 1. a série é suavizada com média móvel centrada de [SMOOTH_WINDOW] pontos;
 * 2. subida e descida só contam depois de variar pelo menos [THRESHOLD_M] desde o último
 *    ponto de referência (histerese).
 */
object ElevationCalculator {
    const val SMOOTH_WINDOW = 5
    const val THRESHOLD_M = 3.0
    const val MAX_SAMPLES = 300

    /** null se o percurso não tem altitude suficiente para um perfil. */
    fun compute(points: List<TrackPoint>): ElevationProfile? {
        val dist = ArrayList<Double>()
        val alt = ArrayList<Double>()
        var total = 0.0
        for (i in points.indices) {
            if (i > 0) total += Geo.distanceMeters(points[i - 1], points[i])
            val a = points[i].altitude ?: continue
            dist += total
            alt += a
        }
        if (alt.size < 2) return null

        val smooth = smooth(alt, SMOOTH_WINDOW)
        // Conta de extremo a extremo; a virada só é confirmada depois de voltar THRESHOLD_M.
        var gain = 0.0
        var loss = 0.0
        var ref = smooth.first()   // último extremo confirmado
        var peak = ref             // extremo do trecho atual
        var rising = true
        for (v in smooth) {
            if (rising) {
                if (v > peak) {
                    peak = v
                } else if (peak - v >= THRESHOLD_M) {
                    if (peak - ref >= THRESHOLD_M) gain += peak - ref
                    ref = peak; peak = v; rising = false
                }
            } else {
                if (v < peak) {
                    peak = v
                } else if (v - peak >= THRESHOLD_M) {
                    if (ref - peak >= THRESHOLD_M) loss += ref - peak
                    ref = peak; peak = v; rising = true
                }
            }
        }
        if (rising && peak - ref >= THRESHOLD_M) gain += peak - ref
        if (!rising && ref - peak >= THRESHOLD_M) loss += ref - peak

        val pairs = dist.indices.map { dist[it] to smooth[it] }
        return ElevationProfile(
            gainM = gain,
            lossM = loss,
            minM = smooth.min(),
            maxM = smooth.max(),
            samples = downsample(pairs, MAX_SAMPLES),
        )
    }

    internal fun smooth(values: List<Double>, window: Int): List<Double> {
        val half = window / 2
        return values.indices.map { i ->
            val from = maxOf(0, i - half)
            val to = minOf(values.lastIndex, i + half)
            var sum = 0.0
            for (j in from..to) sum += values[j]
            sum / (to - from + 1)
        }
    }

    private fun <T> downsample(items: List<T>, max: Int): List<T> {
        if (items.size <= max) return items
        val step = (items.size - 1).toDouble() / (max - 1)
        return List(max) { items[Math.round(it * step).toInt()] }
    }
}
