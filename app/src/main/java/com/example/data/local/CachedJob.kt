package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_jobs")
data class CachedJob(
    @PrimaryKey
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val salary: String,
    val jobType: String,
    val category: String,
    val description: String,
    val requirements: String,
    val contactEmail: String,
    val postedAgo: String,
    val isFeatured: Boolean = false,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
)
