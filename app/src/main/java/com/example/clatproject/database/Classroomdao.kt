package com.example.clatproject.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface Classroomdao {
    @Upsert
    suspend fun insertAll(classrooms: List<Classroom>)

    @Upsert
    suspend fun insertClassroom(classroom: Classroom)

    @Query("SELECT * FROM classrooms")
    suspend fun getClassroom(): List<Classroom>

    @Query("DELETE FROM classrooms")
    suspend fun deleteAll()

    @Query("""
        SELECT * FROM classrooms 
        WHERE UPPER(dept) = UPPER(:targetDept) 
        AND UPPER(type) = UPPER(:requiredType) 
        AND roomNo NOT IN (
            SELECT roomNo FROM allocations 
            WHERE slotName = :slot
        ) 
        LIMIT 2
    """)
    suspend fun findAvailableRoom(targetDept: String, slot: String, requiredType: String): Classroom?

    @Query("SELECT COUNT(*) FROM classrooms")
    suspend fun getTotalRoomsCountSync(): Int

    @Query("SELECT COUNT(*) FROM classrooms")
    fun getTotalRoomsCount(): LiveData<Int>

    @Query("""
        SELECT * FROM classrooms
        WHERE UPPER(dept) = UPPER(:dept) 
        AND building = :building
        AND roomNo NOT IN (
            SELECT roomNo FROM allocations WHERE slotName = :slot
        )
    """)
    suspend fun getAvailableRooms(dept: String, slot: String, building: String): List<Classroom>

    @Query("""
        SELECT * FROM classrooms
        WHERE UPPER(dept) = UPPER(:dept) 
        AND building = :building
        AND roomNo LIKE :floorPrefix || '%'
        AND roomNo NOT IN (
            SELECT roomNo FROM allocations WHERE slotName = :slot
        )
    """)
    suspend fun getAvailableRooms(dept: String, slot: String, building: String, floorPrefix: String): List<Classroom>

    @Query("SELECT * FROM classrooms WHERE UPPER(dept) = UPPER(:dept) AND UPPER(type) = UPPER(:type)")
    suspend fun getRoomsByDept(dept: String, type: String): List<Classroom>
}