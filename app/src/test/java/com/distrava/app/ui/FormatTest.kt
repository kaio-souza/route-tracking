package com.distrava.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun formatos() {
        assertEquals("12,34 km", Format.distance(12_340.0))
        assertEquals("18,7 km/h", Format.speed(18.7 / 3.6))
        assertEquals("0:42:10", Format.duration(2_530_000))
        assertEquals("12:00:00", Format.duration(43_200_000))
    }
}
