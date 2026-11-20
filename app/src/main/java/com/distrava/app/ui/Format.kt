package com.distrava.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val ptBR = Locale("pt", "BR")

    fun distance(meters: Double): String = String.format(ptBR, "%.2f km", meters / 1000.0)

    fun distanceValue(meters: Double): String = String.format(ptBR, "%.2f", meters / 1000.0)

    fun speed(mps: Double): String = String.format(ptBR, "%.1f km/h", mps * 3.6)

    fun speedValue(mps: Double): String = String.format(ptBR, "%.1f", mps * 3.6)

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return String.format(ptBR, "%d:%02d:%02d", h, m, s)
    }

    fun date(epochMs: Long): String = SimpleDateFormat("dd/MM/yy", ptBR).format(Date(epochMs))

    fun dateTime(epochMs: Long): String =
        SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", ptBR).format(Date(epochMs))
}
