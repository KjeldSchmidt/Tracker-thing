package com.paintracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrackerDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_ENTRIES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_PAIN_LEVEL TEXT NOT NULL,
                $COL_PAIN_TYPE TEXT NOT NULL,
                $COL_MENTAL_STATE TEXT NOT NULL,
                $COL_ACTIVITIES TEXT NOT NULL,
                $COL_COMMENTS TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No-op for initial schema versions.
    }

    suspend fun insertEntry(
        painLevel: PainLevel,
        painType: PainType,
        mentalState: String,
        activities: String,
        comments: String
    ): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, System.currentTimeMillis())
            put(COL_PAIN_LEVEL, painLevel.name)
            put(COL_PAIN_TYPE, painType.name)
            put(COL_MENTAL_STATE, mentalState)
            put(COL_ACTIVITIES, activities)
            put(COL_COMMENTS, comments)
        }
        writableDatabase.insert(TABLE_ENTRIES, null, values)
    }

    suspend fun getAllEntries(): List<TrackerEntry> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TrackerEntry>()
        val cursor = readableDatabase.query(
            TABLE_ENTRIES,
            arrayOf(
                COL_ID,
                COL_TIMESTAMP,
                COL_PAIN_LEVEL,
                COL_PAIN_TYPE,
                COL_MENTAL_STATE,
                COL_ACTIVITIES,
                COL_COMMENTS
            ),
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list += TrackerEntry(
                    id = it.getLong(0),
                    timestampEpochMillis = it.getLong(1),
                    painLevel = PainLevel.fromName(it.getString(2)),
                    painType = PainType.fromName(it.getString(3)),
                    mentalState = it.getString(4),
                    activitiesPreviousHours = it.getString(5),
                    comments = it.getString(6)
                )
            }
        }
        list
    }

    companion object {
        const val DB_NAME = "pain_tracker.db"
        private const val DB_VERSION = 1

        private const val TABLE_ENTRIES = "entries"
        private const val COL_ID = "id"
        private const val COL_TIMESTAMP = "timestamp_epoch_millis"
        private const val COL_PAIN_LEVEL = "pain_level"
        private const val COL_PAIN_TYPE = "pain_type"
        private const val COL_MENTAL_STATE = "mental_state"
        private const val COL_ACTIVITIES = "activities_previous_hours"
        private const val COL_COMMENTS = "comments"
    }
}
