package com.example.clatproject.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AllocationDao {

    // 1. Standard Insert: Adds one allocation record
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(allocation: Allocation)

    // 2. Bulk Insert: Useful for the CSV import process
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(allocations: List<Allocation>)

    // 3. The "Smart" Query:
    // This finds who is in a specific room RIGHT NOW by looking up the slot first.
    @Query("""
        SELECT * FROM allocations 
        WHERE roomNo = :roomNo
        AND slotName = (
            SELECT slotName FROM slots 
            WHERE day = :day 
            AND :currentTime BETWEEN startTime AND endTime 
            LIMIT 1
        )
    """)
    suspend fun getFacultyInRoomNow(roomNo: String, day: String, currentTime: String): Allocation?

    // 4. Manual Lookup: Search by a specific slot name (e.g., "A1")
    @Query("SELECT * FROM allocations WHERE roomNo = :roomNo AND slotName = :slotName")
    suspend fun getAllocationBySlot(roomNo: String, slotName: String): Allocation?

    // 5. Clean up: Wipes the table before re-importing CSV
    @Query("DELETE FROM allocations")
    suspend fun deleteAll()

    // 6. Debugging: Get everything to see if data exists
    @Query("SELECT * FROM allocations")
    suspend fun getAll(): List<Allocation>

    @Query("SELECT * FROM allocations")
    suspend fun getAllSync(): List<Allocation>

    @Query("SELECT * FROM allocations WHERE sessionId = :sid")
    suspend fun getSessionAllocations(sid: Long): List<Allocation>

    @Query("SELECT COUNT(*) FROM allocations")
    fun getTotalAssignedCount(): LiveData<Int>

    // In AllocationDao
    @Query("""
    SELECT COUNT(*) FROM allocations 
    WHERE slotName IN (
        SELECT slotName FROM slots 
        WHERE startTime <= :currentTime AND endTime >= :currentTime
    )
""")
    fun getCurrentlyOccupiedCount(currentTime: String): LiveData<Int>

    @Query("""
    SELECT COUNT(*) FROM allocations 
    WHERE EXISTS (
        SELECT 1 FROM slots 
        WHERE UPPER(slots.day) = UPPER(:day)
        AND slots.startTime <= :currentTime 
        AND slots.endTime >= :currentTime
        AND (
            allocations.slotName = slots.slotName
            OR allocations.slotName LIKE '%' || slots.slotName || '%'
        )
    )
""")
    suspend fun getCurrentlyOccupiedCountSync(currentTime: String, day: String): Int

    @Query("""
    SELECT * FROM slots 
    WHERE UPPER(day) = UPPER(:day) 
    AND startTime <= :currentTime AND endTime >= :currentTime
""")
    suspend fun getActiveSlots(day: String, currentTime: String): List<Slot>

    @Query("SELECT * FROM allocations WHERE slotName = :slotName")
    suspend fun getAllocationsBySlot(slotName: String): List<Allocation>

    @Query("SELECT * FROM slots")
    suspend fun getAllSlots(): List<Slot>
}