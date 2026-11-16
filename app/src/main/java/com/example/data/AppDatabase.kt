package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SmsRecord::class, PendingInvoiceEntity::class, RetryAttemptEventEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_records ADD COLUMN nextRetryAtMs INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS retry_attempt_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        smsRecordId INTEGER NOT NULL,
                        attemptedAtMs INTEGER NOT NULL,
                        succeeded INTEGER NOT NULL,
                        httpCode INTEGER,
                        isAutoRetry INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_retry_attempt_events_attemptedAtMs ON retry_attempt_events (attemptedAtMs)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_retry_attempt_events_smsRecordId ON retry_attempt_events (smsRecordId)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_invoices ADD COLUMN matchedAtMs INTEGER")
                db.execSQL("ALTER TABLE pending_invoices ADD COLUMN bankTitle TEXT")
            }
        }

        @Suppress("DEPRECATION")
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "asis_panel_logs.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
