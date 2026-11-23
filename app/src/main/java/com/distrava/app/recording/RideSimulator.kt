package com.distrava.app.recording

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Gera um percurso falso (só em builds de debug) para testar gravação, mapa e imagem
 * sem sair de casa. Volta na orla de Boa Viagem, Recife, a ~23 km/h com uma parada.
 *
 * Disparo: adb shell am start -n com.distrava.app/.MainActivity --ez simulate_ride true
 */
object RideSimulator {
    // Av. Boa Viagem (geometria tirada dos tiles do OSM), ida e volta.
    private val avenue = listOf(
        -8.11547 to -34.89223,
        -8.12196 to -34.89600,
        -8.12300 to -34.89647,
        -8.12437 to -34.89686,
        -8.12657 to -34.89765,
        -8.12992 to -34.89895,
        -8.13250 to -34.90013,
        -8.13517 to -34.90131,
        -8.14078 to -34.90314,
        -8.14318 to -34.90415,
        -8.14505 to -34.90511,
        -8.14658 to -34.90585,
    )
    private val waypoints = avenue + avenue.reversed().drop(1)

    private const val SPEED_MPS = 6.5
    private val STOP_AT_WAYPOINT = avenue.size - 1
    private const val STOP_SECONDS = 30

    /** Um ponto por segundo simulado. */
    fun generate(startTime: Long, seed: Int = 42): List<TrackPoint> {
        val rnd = Random(seed)
        val out = mutableListOf<TrackPoint>()
        var t = startTime
        var progress = 0.0 // 0..1 ao longo do percurso, para a altitude
        fun emit(lat: Double, lon: Double, speed: Float) {
            // ruído de ~3 m
            val jLat = (rnd.nextDouble() - 0.5) * 0.00005
            val jLon = (rnd.nextDouble() - 0.5) * 0.00005
            // Altitude fictícia com duas subidas suaves e ruído de ~1 m, para testar a altimetria.
            val alt = 8.0 + 12.0 * sin(progress * Math.PI * 2).let { it * it } + (rnd.nextDouble() - 0.5) * 2.0
            out += TrackPoint(t, lat + jLat, lon + jLon, alt, speed, 4f + rnd.nextFloat() * 4f)
            t += 1000
        }
        for (i in 0 until waypoints.size - 1) {
            val (lat0, lon0) = waypoints[i]
            val (lat1, lon1) = waypoints[i + 1]
            val dist = Geo.distanceMeters(lat0, lon0, lat1, lon1)
            val steps = (dist / SPEED_MPS).toInt().coerceAtLeast(1)
            for (s in 0 until steps) {
                val f = s.toDouble() / steps
                progress = (i + f) / (waypoints.size - 1)
                // leve variação de velocidade ao longo do trecho
                val wobble = 1.0 + 0.15 * sin(f * 6.0) * cos(i.toDouble())
                emit(lat0 + (lat1 - lat0) * f, lon0 + (lon1 - lon0) * f, (SPEED_MPS * wobble).toFloat())
            }
            if (i + 1 == STOP_AT_WAYPOINT) repeat(STOP_SECONDS) { emit(lat1, lon1, 0f) }
        }
        val (latEnd, lonEnd) = waypoints.last()
        emit(latEnd, lonEnd, 0f)
        return out
    }
}
