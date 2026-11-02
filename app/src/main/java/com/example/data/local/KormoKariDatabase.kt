package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [CachedJob::class, CachedApplication::class],
    version = 1,
    exportSchema = false
)
abstract class KormoKariDatabase : RoomDatabase() {

    abstract fun jobDao(): CachedJobDao
    abstract fun applicationDao(): CachedApplicationDao

    companion object {
        @Volatile
        private var INSTANCE: KormoKariDatabase? = null

        fun getDatabase(context: Context): KormoKariDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KormoKariDatabase::class.java,
                    "kormokari_local_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialCache(database)
                    }
                }
            }
        }

        suspend fun populateInitialCache(database: KormoKariDatabase) {
            val jobDao = database.jobDao()
            val applicationDao = database.applicationDao()

            if (jobDao.getCount() == 0) {
                jobDao.insertJobs(DEFAULT_CACHED_JOBS)
            }
            if (applicationDao.getCount() == 0) {
                applicationDao.insertApplications(DEFAULT_CACHED_APPLICATIONS)
            }
        }

        val DEFAULT_CACHED_JOBS = listOf(
            CachedJob(
                id = "job-101",
                title = "Logistics Delivery Associate",
                company = "KormoKari Express Fleet",
                location = "Dhaka Central / Gulshan",
                salary = "৳22,000 - ৳28,000 / month",
                jobType = "Full-time",
                category = "Delivery",
                description = "Responsible for daily e-commerce and retail parcel distribution across key commercial hubs. Fuel allowance and performance incentives provided.",
                requirements = "Valid 2-wheeler license, smartphone with GPS, punctuality, basic communication skills.",
                contactEmail = "careers@kormokari.com",
                postedAgo = "2 hours ago",
                isFeatured = true
            ),
            CachedJob(
                id = "job-102",
                title = "Field Service Electrical Technician",
                company = "Apex Engineering & Maintenance",
                location = "Chittagong Industrial Zone",
                salary = "৳30,000 - ৳38,000 / month",
                jobType = "Full-time",
                category = "Skilled Trades",
                description = "Carry out preventive maintenance, wiring inspections, and diagnostics on commercial refrigeration and power backup systems.",
                requirements = "Diploma or trade certification in Electrical Engineering, minimum 1 year practical site experience.",
                contactEmail = "support@apex-maintenance.bd",
                postedAgo = "5 hours ago",
                isFeatured = true
            ),
            CachedJob(
                id = "job-103",
                title = "Hospitality Front Desk Host",
                company = "Grand Panorama Suites",
                location = "Sylhet City Center",
                salary = "৳24,000 - ৳29,000 / month",
                jobType = "Shift-based",
                category = "Hospitality",
                description = "Warmly welcome guests, manage reservation confirmations, handle check-ins and direct customer inquiries with exceptional hospitality.",
                requirements = "Fluency in Bengali and conversational English, professional presentation, customer-first attitude.",
                contactEmail = "hr@grandpanoramasuites.com",
                postedAgo = "1 day ago",
                isFeatured = false
            ),
            CachedJob(
                id = "job-104",
                title = "Customer Support Specialist",
                company = "ConnectCare BPO Solutions",
                location = "Mohakhali, Dhaka (Hybrid)",
                salary = "৳26,000 - ৳32,000 / month",
                jobType = "Full-time",
                category = "Customer Service",
                description = "Handle omnichannel inquiries (phone, in-app messaging, email) for e-commerce and financial services customers.",
                requirements = "HSC/Graduate, pleasant phone demeanor, rapid typing speed (40+ WPM), problem-solving skills.",
                contactEmail = "jobs@connectcare-bpo.com",
                postedAgo = "1 day ago",
                isFeatured = false
            ),
            CachedJob(
                id = "job-105",
                title = "Retail Store Inventory Supervisor",
                company = "Shwapno Market Hubs",
                location = "Uttara, Dhaka",
                salary = "৳25,000 - ৳30,000 / month",
                jobType = "Full-time",
                category = "Retail",
                description = "Oversee incoming stock verifications, shelf restocking, inventory audits, and coordinate floor staff schedules.",
                requirements = "Previous retail supervisor experience, proficiency in handheld barcode scanners and stock sheets.",
                contactEmail = "recruitment@shwapnohubs.com",
                postedAgo = "2 days ago",
                isFeatured = false
            )
        )

        val DEFAULT_CACHED_APPLICATIONS = listOf(
            CachedApplication(
                id = "app-501",
                jobTitle = "Logistics Delivery Associate",
                company = "KormoKari Express Fleet",
                status = "Under Review",
                appliedDate = "Yesterday, 3:45 PM",
                note = "Application received and routed to regional dispatch team. Keep notifications enabled for interview calls."
            ),
            CachedApplication(
                id = "app-502",
                jobTitle = "Customer Support Specialist",
                company = "ConnectCare BPO Solutions",
                status = "Interview Scheduled",
                appliedDate = "3 days ago",
                note = "Virtual screening scheduled. Ensure phone is reachable between 10:00 AM - 12:00 PM."
            )
        )
    }
}
