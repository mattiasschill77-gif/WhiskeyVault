package com.schill.whiskeyvault

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Whiskey::class], version = 2, exportSchema = false) // Höj version till 2
abstract class WhiskeyDatabase : RoomDatabase() {
    abstract fun whiskeyDao(): WhiskeyDao

    companion object {
        @Volatile private var INSTANCE: WhiskeyDatabase? = null
        fun getDatabase(context: Context): WhiskeyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WhiskeyDatabase::class.java,
                    "whiskey_database"
                )
                    .fallbackToDestructiveMigration() // Rensar databasen vid ändring
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}