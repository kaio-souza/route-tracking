package com.distrava.app.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationFilterTest {
    private val a = TrackPoint(time = 1_000, lat = -8.0, lon = -35.0, accuracyM = 5f)

    @Test
    fun `primeiro ponto preciso e aceito`() {
        assertEquals(LocationFilter.Result.ACCEPTED, LocationFilter.check(null, a))
    }

    @Test
    fun `baixa precisao e descartada`() {
        assertEquals(LocationFilter.Result.LOW_ACCURACY, LocationFilter.check(null, a.copy(accuracyM = 40f)))
    }

    @Test
    fun `ponto fora de ordem e descartado`() {
        assertEquals(LocationFilter.Result.OUT_OF_ORDER, LocationFilter.check(a, a.copy(time = 1_000)))
    }

    @Test
    fun `salto de GPS e descartado`() {
        // ~1,1 km em 1 s
        val jump = a.copy(time = 2_000, lat = -7.99)
        assertEquals(LocationFilter.Result.JUMP, LocationFilter.check(a, jump))
    }

    @Test
    fun `movimento normal e aceito`() {
        // ~11 m em 2 s ≈ 20 km/h
        val next = a.copy(time = 3_000, lat = -7.9999)
        assertEquals(LocationFilter.Result.ACCEPTED, LocationFilter.check(a, next))
    }
}
