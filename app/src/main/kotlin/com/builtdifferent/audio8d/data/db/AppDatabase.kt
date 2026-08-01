package com.builtdifferent.audio8d.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ConversionEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversionDao(): ConversionDao

    companion object {
        const val DB_NAME = "audio8d.db"
    }
}
