    package com.example.clatproject.database

    import androidx.room.Entity

    @Entity(
        tableName = "allocations",
        primaryKeys = ["roomNo", "slotName"]
    )
    data class Allocation(
        val roomNo: String,
        val building: String,
        val slotName: String,
        val dept: String,
        val subject: String,
        val type: String,
        val courseCode: String,
        val facultyName: String,
        val facultyId: String? = null,
        val sessionId: Long
    )