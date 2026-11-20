package com.distrava.app.recording

import com.distrava.app.data.DiscardReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RecordingState {
    data object Idle : RecordingState

    data class Recording(
        val activityId: Long,
        val startedAt: Long,
        val points: List<TrackPoint>,
        val distanceMeters: Double,
        val currentSpeedMps: Double,
        val gpsAccuracyM: Float?,
    ) : RecordingState

    /** Finalizando: salvando pontos, calculando e gerando a imagem. */
    data object Finishing : RecordingState

    data class Finished(val activityId: Long) : RecordingState

    data class Discarded(val reason: DiscardReason) : RecordingState
}

/** Estado da gravação compartilhado entre o serviço e a UI. */
object RecordingStateHolder {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    internal fun set(value: RecordingState) {
        _state.value = value
    }

    /** A UI chama depois de tratar Finished/Discarded. */
    fun acknowledge() {
        val s = _state.value
        if (s is RecordingState.Finished || s is RecordingState.Discarded) _state.value = RecordingState.Idle
    }
}
