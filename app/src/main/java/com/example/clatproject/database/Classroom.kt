package com.example.clatproject.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "classrooms",
    primaryKeys = ["roomNo", "building"] // This replaces the ID
)
data class Classroom(
    val roomNo: String ="",
    val building: String ="",
    val dept: String ="",
    val type: String=""
)
