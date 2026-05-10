package com.example.clatproject.database

import androidx.room.Entity

@Entity(
    tableName = "slots",
    primaryKeys = ["slotName", "day"]
)
data class Slot(
    val slotName: String,
    val day: String,
    val startTime: String,
    val endTime: String,
    val type: String
)
