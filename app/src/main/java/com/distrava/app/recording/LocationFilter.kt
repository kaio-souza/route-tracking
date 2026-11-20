package com.distrava.app.recording

/**
 * Descarta pontos ruins de GPS (docs/technical.md, seção 3).
 */
object LocationFilter {
    const val MAX_ACCURACY_M = 25f
    const val MAX_SPEED_MPS = 100.0 / 3.6

    enum class Result { ACCEPTED, LOW_ACCURACY, OUT_OF_ORDER, JUMP }

    fun check(lastAccepted: TrackPoint?, candidate: TrackPoint): Result {
        if (candidate.accuracyM > MAX_ACCURACY_M) return Result.LOW_ACCURACY
        if (lastAccepted == null) return Result.ACCEPTED
        if (candidate.time <= lastAccepted.time) return Result.OUT_OF_ORDER
        val dtSec = (candidate.time - lastAccepted.time) / 1000.0
        val impliedSpeed = Geo.distanceMeters(lastAccepted, candidate) / dtSec
        if (impliedSpeed > MAX_SPEED_MPS) return Result.JUMP
        return Result.ACCEPTED
    }
}
