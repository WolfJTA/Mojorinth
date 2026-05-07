package com.example.modrinthforandroid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities  = [InstalledMod::class],
    version   = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun installedModDao(): InstalledModDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mojorinth.db"
                ).build().also { INSTANCE = it }
            }
    }
}
