package com.example.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class OfflineJobRepository(private val database: KormoKariDatabase) {

    constructor(context: Context) : this(KormoKariDatabase.getDatabase(context))

    val allJobs: Flow<List<CachedJob>> = database.jobDao().getAllJobs()
    val allApplications: Flow<List<CachedApplication>> = database.applicationDao().getAllApplications()

    fun searchJobs(query: String): Flow<List<CachedJob>> {
        return if (query.isBlank()) {
            database.jobDao().getAllJobs()
        } else {
            database.jobDao().searchJobs(query.trim())
        }
    }

    fun getJobsByCategory(category: String): Flow<List<CachedJob>> {
        return database.jobDao().getJobsByCategory(category)
    }

    fun getJobById(id: String): Flow<CachedJob?> {
        return database.jobDao().getJobById(id)
    }

    suspend fun ensureDefaultCachePopulated() = withContext(Dispatchers.IO) {
        KormoKariDatabase.populateInitialCache(database)
    }

    suspend fun cacheJob(job: CachedJob) = withContext(Dispatchers.IO) {
        database.jobDao().insertJob(job)
    }

    suspend fun cacheJobs(jobs: List<CachedJob>) = withContext(Dispatchers.IO) {
        database.jobDao().insertJobs(jobs)
    }

    suspend fun cacheApplication(app: CachedApplication) = withContext(Dispatchers.IO) {
        database.applicationDao().insertApplication(app)
    }
}
