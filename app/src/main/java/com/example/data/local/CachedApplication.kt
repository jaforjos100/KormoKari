package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_applications")
data class CachedApplication(
    @PrimaryKey
    val id: String,
    val jobTitle: String,
    val company: String,
    val status: String,
    val appliedDate: String,
    val note: String,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
)
