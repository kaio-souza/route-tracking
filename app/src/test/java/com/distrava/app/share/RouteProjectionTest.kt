package com.distrava.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProjectionTest {

    @Test
    fun `rota cabe no retangulo e fica centralizada`() {
        val route = listOf(-8.0 to -35.0, -8.1 to -35.0, -8.1 to -34.8)
        val px = RouteProjection.fit(route, left = 100f, top = 200f, width = 800f, height = 600f)
        px.forEach {
            assertTrue(it.x in 99.9f..900.1f)
            assertTrue(it.y in 199.9f..800.1f)
        }
        // Mais larga que alta: ocupa toda a largura.
        assertEquals(100f, px.minOf { it.x }, 0.5f)
        assertEquals(900f, px.maxOf { it.x }, 0.5f)
    }

    @Test
    fun `norte fica em cima`() {
        val px = RouteProjection.fit(listOf(-8.0 to -35.0, -8.1 to -35.0), 0f, 0f, 100f, 100f)
        assertTrue(px[0].y < px[1].y)
    }

    @Test
    fun `ponto unico nao quebra`() {
        val px = RouteProjection.fit(listOf(-8.0 to -35.0), 0f, 0f, 100f, 100f)
        assertEquals(50f, px[0].x, 0.01f)
        assertEquals(50f, px[0].y, 0.01f)
    }

    @Test
    fun `downsample mantem extremos`() {
        val list = (0..1000).toList()
        val out = RouteProjection.downsample(list, 50)
        assertEquals(50, out.size)
        assertEquals(0, out.first())
        assertEquals(1000, out.last())
    }
}
