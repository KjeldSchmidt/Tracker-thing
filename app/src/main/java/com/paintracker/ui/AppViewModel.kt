package com.paintracker.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paintracker.data.PainLevel
import com.paintracker.data.PainType
import com.paintracker.data.ReminderRepository
import com.paintracker.data.TrackerDatabaseHelper
import com.paintracker.data.TrackerEntry
import com.paintracker.export.ExportManager
import com.paintracker.reminders.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File

data class FormState(
    val painLevel: PainLevel = PainLevel.NONE,
    val painType: PainType = PainType.CONTINUOUS,
    val mentalState: String = "",
    val activities: String = "",
    val comments: String = ""
)

data class UiState(
    val form: FormState = FormState(),
    val entries: List<TrackerEntry> = emptyList(),
    val reminderTimes: List<String> = emptyList(),
    val reminderInput: String = "",
    val message: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = TrackerDatabaseHelper(application)
    private val reminderRepository = ReminderRepository(application)
    private val exportManager = ExportManager(application)

    private val entries = MutableStateFlow<List<TrackerEntry>>(emptyList())
    private val formState = MutableStateFlow(FormState())
    private val reminderInput = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    val uiState: StateFlow<UiState> = combine(
        entries,
        reminderRepository.reminderTimes,
        formState,
        reminderInput,
        message
    ) { allEntries, times, form, input, msg ->
        UiState(
            form = form,
            entries = allEntries,
            reminderTimes = times,
            reminderInput = input,
            message = msg
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UiState()
    )

    init {
        viewModelScope.launch {
            refreshEntries()
            ReminderScheduler.scheduleAll(getApplication(), reminderRepository.readTimesOnce())
        }
    }

    fun ensureReminderSchedule() {
        viewModelScope.launch {
            ReminderScheduler.scheduleAll(getApplication(), reminderRepository.readTimesOnce())
        }
    }

    fun updatePainLevel(value: PainLevel) {
        formState.value = formState.value.copy(painLevel = value)
    }

    fun updatePainType(value: PainType) {
        formState.value = formState.value.copy(painType = value)
    }

    fun updateMentalState(value: String) {
        formState.value = formState.value.copy(mentalState = value)
    }

    fun updateActivities(value: String) {
        formState.value = formState.value.copy(activities = value)
    }

    fun updateComments(value: String) {
        formState.value = formState.value.copy(comments = value)
    }

    fun saveEntry() {
        val current = formState.value
        viewModelScope.launch {
            db.insertEntry(
                painLevel = current.painLevel,
                painType = current.painType,
                mentalState = current.mentalState.trim(),
                activities = current.activities.trim(),
                comments = current.comments.trim()
            )
            formState.value = FormState()
            refreshEntries()
            message.value = "Entry saved"
        }
    }

    fun updateReminderInput(value: String) {
        reminderInput.value = value
    }

    fun addReminderTime() {
        val value = reminderInput.value.trim()
        if (!value.matches(Regex("""\d{2}:\d{2}"""))) {
            message.value = "Use HH:mm format"
            return
        }
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            message.value = "Invalid time"
            return
        }
        viewModelScope.launch {
            val current = reminderRepository.readTimesOnce()
            val updated = (current + value).distinct().sorted()
            reminderRepository.saveReminderTimes(updated)
            ReminderScheduler.scheduleAll(getApplication(), updated)
            reminderInput.value = ""
            message.value = "Reminder added"
        }
    }

    fun removeReminderTime(value: String) {
        viewModelScope.launch {
            val updated = reminderRepository.readTimesOnce().filterNot { it == value }
            reminderRepository.saveReminderTimes(updated)
            ReminderScheduler.scheduleAll(getApplication(), updated)
            message.value = "Reminder removed"
        }
    }

    fun exportCsvFromActivity(onIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            val file = exportManager.exportCsv(db.getAllEntries())
            if (file == null) {
                message.value = "CSV export failed"
            } else {
                onIntent(buildShareIntent(file, "text/csv"))
            }
        }
    }

    fun exportSqliteFromActivity(onIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            val file = exportManager.exportSqliteCopy()
            if (file == null) {
                message.value = "SQLite export failed"
            } else {
                onIntent(buildShareIntent(file, "application/octet-stream"))
            }
        }
    }

    private fun buildShareIntent(file: File, mimeType: String): Intent {
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun formatTime(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(timestampFormatter)
    }

    private suspend fun refreshEntries() {
        entries.value = db.getAllEntries()
    }
}
