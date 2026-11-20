package com.distrava.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun get(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities WHERE id = :id")
    fun observe(id: Long): Flow<ActivityEntity?>

    @Query("SELECT * FROM activities WHERE status = '${ActivityStatus.FINISHED}' ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE status = '${ActivityStatus.RECORDING}' ORDER BY startedAt DESC LIMIT 1")
    suspend fun findRecording(): ActivityEntity?

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE activityId = :activityId ORDER BY time ASC")
    suspend fun getPoints(activityId: Long): List<TrackPointEntity>
}
