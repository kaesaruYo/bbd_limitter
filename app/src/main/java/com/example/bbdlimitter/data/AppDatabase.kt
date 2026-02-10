package com.example.bbdlimitter.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProductEntity::class, LocationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun locationDao(): LocationDao
}
