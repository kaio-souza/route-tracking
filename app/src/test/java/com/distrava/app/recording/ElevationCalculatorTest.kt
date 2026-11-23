package com.distrava.app.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationCalculatorTest {

    private val degPer100m = 100.0 / 111_195.0

    /** Um ponto a cada 10 m, com a altitude dada por índice. */
    private fun ride(altitudes: List<Double?>) = altitudes.mapIndexed { i, a ->
        TrackPoint(time = i * 1000L, lat = -8.0 + i * degPer100m / 10.0, lon = -35.0, altitude = a)
    }

    @Test
    fun `subida e descida constantes`() {
        // sobe 50 m em 51 pontos, depois desce 50 m
        val up = (0..50).map { it.toDouble() }
        val down = (49 downTo 0).map { it.toDouble() }
        val p = ElevationCalculator.compute(ride(up + down))
        assertNotNull(p)
        assertEquals(50.0, p!!.gainM, 3.0)
        assertEquals(50.0, p.lossM, 3.0)
        assertEquals(0.0, p.minM, 1.0)
        assertEquals(50.0, p.maxM, 2.0) // a suavização arredonda o pico
    }

    @Test
    fun `ruido em terreno plano nao gera ganho`() {
        // oscila +-2 m em volta de 10 m
        val noisy = List(200) { 10.0 + if (it % 2 == 0) 2.0 else -2.0 }
        val p = ElevationCalculator.compute(ride(noisy))!!
        assertEquals(0.0, p.gainM, 0.0)
        assertEquals(0.0, p.lossM, 0.0)
    }

    @Test
    fun `pontos sem altitude sao ignorados mas distancia conta`() {
        val alts = listOf(0.0, null, null, 10.0, null, 20.0)
        val p = ElevationCalculator.compute(ride(alts))!!
        assertEquals(3, p.samples.size)
        assertEquals(50.0, p.samples.last().first, 1.0)
    }

    @Test
    fun `sem altitude retorna null`() {
        assertNull(ElevationCalculator.compute(ride(listOf(null, null, null))))
        assertNull(ElevationCalculator.compute(emptyList()))
    }

    @Test
    fun `grafico e reduzido`() {
        val p = ElevationCalculator.compute(ride(List(2000) { it * 0.1 }))!!
        assertTrue(p.samples.size <= ElevationCalculator.MAX_SAMPLES)
    }
}
