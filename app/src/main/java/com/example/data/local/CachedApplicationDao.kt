package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedApplicationDao {

    @Query("SELECT * FROM cached_applications ORDER BY cachedAtTimestamp DESC")
    fun getAllApplications(): Flow<List<CachedApplication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplications(applications: List<CachedApplication>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(application: CachedApplication)

    @Query("DELETE FROM cached_applications")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM cached_applications")
    suspend fun getCount(): Int
}
