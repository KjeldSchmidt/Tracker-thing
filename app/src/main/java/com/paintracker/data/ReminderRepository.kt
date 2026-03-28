package com.paintracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "reminder_preferences"
private const val DEFAULT_TIMES = "09:00,13:00,18:00"
private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

class ReminderRepository(private val context: Context) {
    private val key = stringPreferencesKey("times_csv")
    private val regex = Regex("""\d{2}:\d{2}""")

    val reminderTimes: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val stored = prefs[key] ?: DEFAULT_TIMES
        normalize(stored.split(","))
    }

    suspend fun saveReminderTimes(times: List<String>) {
        val clean = normalize(times)
        context.dataStore.edit { prefs ->
            prefs[key] = if (clean.isEmpty()) DEFAULT_TIMES else clean.joinToString(",")
        }
    }

    suspend fun readTimesOnce(): List<String> = reminderTimes.first()

    private fun normalize(values: List<String>): List<String> {
        return values
            .map { it.trim() }
            .filter { regex.matches(it) }
            .distinct()
            .sorted()
    }
}
