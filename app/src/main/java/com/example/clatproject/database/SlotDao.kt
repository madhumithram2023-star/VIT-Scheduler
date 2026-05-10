package com.example.clatproject.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SlotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(slots: List<Slot>)

    @Query("DELETE FROM slots")
    suspend fun deleteAll()

    @Query("SELECT * FROM slots WHERE day = :day")
    suspend fun getSlotsByDay(day: String): List<Slot>

    @Query("SELECT * FROM slots")
    fun getAllSlots(): List<Slot>
}