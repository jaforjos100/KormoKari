package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedJobDao {

    @Query("SELECT * FROM cached_jobs ORDER BY isFeatured DESC, cachedAtTimestamp DESC")
    fun getAllJobs(): Flow<List<CachedJob>>

    @Query("SELECT * FROM cached_jobs WHERE id = :jobId LIMIT 1")
    fun getJobById(jobId: String): Flow<CachedJob?>

    @Query("SELECT * FROM cached_jobs WHERE category = :category ORDER BY isFeatured DESC")
    fun getJobsByCategory(category: String): Flow<List<CachedJob>>

    @Query("""
        SELECT * FROM cached_jobs 
        WHERE title LIKE '%' || :query || '%' 
           OR company LIKE '%' || :query || '%' 
           OR location LIKE '%' || :query || '%' 
           OR category LIKE '%' || :query || '%'
        ORDER BY isFeatured DESC, cachedAtTimestamp DESC
    """)
    fun searchJobs(query: String): Flow<List<CachedJob>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<CachedJob>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: CachedJob)

    @Query("DELETE FROM cached_jobs")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM cached_jobs")
    suspend fun getCount(): Int
}
