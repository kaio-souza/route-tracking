package com.distrava.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.distrava.app.recording.TrackPoint

object ActivityStatus {
    const val RECORDING = "GRAVANDO"
    const val FINISHED = "FINALIZADO"
}

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String = ActivityStatus.RECORDING,
    val distanceMeters: Double = 0.0,
    val totalTimeMs: Long = 0,
    val movingTimeMs: Long = 0,
    val avgSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val imagePath: String? = null,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** Nome dado ao favoritar; null enquanto não é favorito. */
    val name: String? = null,
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("activityId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val speedMps: Float?,
    val accuracyM: Float,
) {
    fun toTrackPoint() = TrackPoint(time, lat, lon, altitude, speedMps, accuracyM)

    companion object {
        fun from(activityId: Long, p: TrackPoint) = TrackPointEntity(
            activityId = activityId,
            time = p.time,
            lat = p.lat,
            lon = p.lon,
            altitude = p.altitude,
            speedMps = p.speedMps,
            accuracyM = p.accuracyM,
        )
    }
}
