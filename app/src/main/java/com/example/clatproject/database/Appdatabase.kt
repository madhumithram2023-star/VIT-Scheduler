package com.example.clatproject.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Classroom::class, Slot::class, Allocation::class], version = 10, exportSchema = false)
abstract class Appdatabase : RoomDatabase() {
    abstract fun Classroomdao(): Classroomdao
    abstract fun SlotDao(): SlotDao
    abstract fun AllocationDao(): AllocationDao
    companion object {
        @Volatile
        private var INSTANCE: Appdatabase? = null

        fun getDatabase(context: Context): Appdatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    Appdatabase::class.java,
                    "classroom_db"
                )
                    .fallbackToDestructiveMigration() // This wipes the old DB if the version changes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
