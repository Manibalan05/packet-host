package com.alan.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Site::class],
    version = 2,
    autoMigrations = [],
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
}
