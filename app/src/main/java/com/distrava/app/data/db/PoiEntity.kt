package com.distrava.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Tipos de ponto de interesse; `key` é o valor gravado no banco. */
enum class PoiKind(val key: String, val color: String) {
    WATER("agua", "#2F80ED"),
    REPAIR("oficina", "#6B5B95"),
    RESTROOM("banheiro", "#00897B"),
    FOOD("comida", "#E53935"),
    OTHER("outro", "#757575");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** Ponto marcado pelo usuário no mapa (oficina, água etc.). */
@Entity(tableName = "pois")
data class PoiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lat: Double,
    val lon: Double,
    val name: String,
    val kind: String,
    val createdAt: Long,
) {
    val poiKind: PoiKind get() = PoiKind.of(kind)
}

@Dao
interface PoiDao {
    @Query("SELECT * FROM pois ORDER BY createdAt")
    fun observeAll(): Flow<List<PoiEntity>>

    @Insert
    suspend fun insert(poi: PoiEntity): Long

    @Query("DELETE FROM pois WHERE id = :id")
    suspend fun delete(id: Long)
}
