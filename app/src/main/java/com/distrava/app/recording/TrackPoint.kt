package com.distrava.app.recording

/** Ponto de GPS em unidades SI. Sem dependência de Android para permitir testes na JVM. */
data class TrackPoint(
    val time: Long,          // epoch ms
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val speedMps: Float? = null,
    val accuracyM: Float = 0f,
)
