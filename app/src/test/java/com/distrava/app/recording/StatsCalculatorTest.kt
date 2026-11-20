package com.distrava.app.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    // ~0,0009 grau de latitude ≈ 100 m.
    private val degPer100m = 100.0 / 111_195.0

    private fun straightLine(n: Int, stepMeters: Double, stepMs: Long, start: Long = 0L) =
        List(n) { i ->
            TrackPoint(time = start + i * stepMs, lat = -8.0 + i * degPer100m * stepMeters / 100.0, lon = -35.0)
        }

    @Test
    fun `distancia e velocidade media em linha reta`() {
        // 11 pontos, 10 trechos de 100 m a cada 20 s = 1000 m em 200 s = 5 m/s (18 km/h)
        val pts = straightLine(11, 100.0, 20_000)
        val stats = StatsCalculator.compute(pts, startedAt = 0, finishedAt = 200_000)
        assertEquals(1000.0, stats.distanceMeters, 2.0)
        assertEquals(200_000L, stats.movingTimeMs)
        assertEquals(5.0, stats.avgSpeedMps, 0.02)
        assertEquals(5.0, stats.maxSpeedMps, 0.02)
    }

    @Test
    fun `parada nao conta como tempo em movimento`() {
        val moving = straightLine(6, 100.0, 20_000) // 500 m em 100 s
        val last = moving.last()
        // parado 5 minutos no mesmo lugar
        val stopped = List(5) { i -> last.copy(time = last.time + (i + 1) * 60_000L) }
        val pts = moving + stopped
        val stats = StatsCalculator.compute(pts, startedAt = 0, finishedAt = pts.last().time)
        assertEquals(100_000L, stats.movingTimeMs)
        assertEquals(400_000L, stats.totalTimeMs)
        assertEquals(5.0, stats.avgSpeedMps, 0.02)
    }

    @Test
    fun `velocidade maxima usa media movel e ignora pico isolado`() {
        // 10 trechos a 5 m/s e um trecho curto a 15 m/s
        val base = straightLine(11, 100.0, 20_000)
        val spike = base.last().let {
            it.copy(time = it.time + 10_000, lat = it.lat + degPer100m * 1.5)
        }
        val stats = StatsCalculator.compute(base + spike, 0, spike.time)
        assertTrue(stats.maxSpeedMps < 15.0)
        assertTrue(stats.maxSpeedMps > 5.0)
    }

    @Test
    fun `percurso curto e invalido`() {
        val pts = straightLine(2, 30.0, 10_000)
        val stats = StatsCalculator.compute(pts, 0, 10_000)
        assertFalse(StatsCalculator.isValidRide(pts, stats))
    }

    @Test
    fun `sem pontos nao quebra`() {
        val stats = StatsCalculator.compute(emptyList(), 0, 1000)
        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertEquals(0.0, stats.avgSpeedMps, 0.0)
        assertFalse(StatsCalculator.isValidRide(emptyList(), stats))
    }
}
