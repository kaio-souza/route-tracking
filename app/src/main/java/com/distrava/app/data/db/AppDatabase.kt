package com.distrava.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Remove a coluna de nome, substituída pelos favoritos. */
@DeleteColumn(tableName = "activities", columnName = "name")
class RemoveRouteName : AutoMigrationSpec

@Database(
    entities = [ActivityEntity::class, TrackPointEntity::class, PoiEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = RemoveRouteName::class),
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun poiDao(): PoiDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "distrava.db").build()
    }
}
