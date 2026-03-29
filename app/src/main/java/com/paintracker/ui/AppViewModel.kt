package com.paintracker.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.content.res.Resources
import android.content.pm.PackageManager
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
import com.paintracker.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.FormatStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Locale

data class FormState(
    val painLevel: PainLevel = PainLevel.NONE,
    val painType: PainType = PainType.NOT_APPLICABLE,
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

data class EditState(
    val entryId: Long = 0L,
    val painLevel: PainLevel = PainLevel.NONE,
    val painType: PainType = PainType.NOT_APPLICABLE,
    val mentalState: String = "",
    val activities: String = "",
    val comments: String = ""
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = TrackerDatabaseHelper(application)
    private val reminderRepository = ReminderRepository(application)
    private val exportManager = ExportManager(application)

    private val entries = MutableStateFlow<List<TrackerEntry>>(emptyList())
    private val formState = MutableStateFlow(FormState())
    private val reminderInput = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)
    private val entryToEdit = MutableStateFlow<EditState?>(null)

    private val resources: Resources = application.resources
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())

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

    val currentEntryToEdit: StateFlow<EditState?> = entryToEdit

    init {
        viewModelScope.launch {
            refreshEntries(showError = false)
            ReminderScheduler.scheduleAll(getApplication(), reminderRepository.readTimesOnce())
        }
    }

    fun ensureReminderSchedule() {
        viewModelScope.launch {
            ReminderScheduler.scheduleAll(getApplication(), reminderRepository.readTimesOnce())
        }
    }

    fun updatePainLevel(value: PainLevel) {
        formState.value = when (value) {
            PainLevel.NONE -> formState.value.copy(
                painLevel = value,
                painType = PainType.NOT_APPLICABLE
            )
            else -> {
                val nextPainType = if (formState.value.painType == PainType.NOT_APPLICABLE) {
                    PainType.CONTINUOUS
                } else {
                    formState.value.painType
                }
                formState.value.copy(painLevel = value, painType = nextPainType)
            }
        }
    }

    fun updatePainType(value: PainType) {
        if (formState.value.painLevel == PainLevel.NONE) {
            formState.value = formState.value.copy(painType = PainType.NOT_APPLICABLE)
            return
        }
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
            runCatching {
                db.insertEntry(
                    painLevel = current.painLevel,
                    painType = if (current.painLevel == PainLevel.NONE) {
                        PainType.NOT_APPLICABLE
                    } else {
                        current.painType
                    },
                    mentalState = current.mentalState.trim(),
                    activities = current.activities.trim(),
                    comments = current.comments.trim()
                )
            }.onSuccess {
                formState.value = FormState(painLevel = PainLevel.NONE, painType = PainType.NOT_APPLICABLE)
                refreshEntries(showError = false)
                message.value = resources.getString(R.string.message_entry_saved)
            }.onFailure {
                message.value = resources.getString(R.string.message_entry_save_failed)
            }
        }
    }

    fun updateReminderInput(value: String) {
        reminderInput.value = value
    }

    fun addReminderTime() {
        val normalized = normalizeReminderInput(reminderInput.value)
        if (normalized == null) {
            message.value = resources.getString(R.string.message_time_format_invalid)
            return
        }
        val parts = normalized.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            message.value = resources.getString(R.string.message_time_invalid)
            return
        }
        viewModelScope.launch {
            val current = reminderRepository.readTimesOnce()
            val updated = (current + normalized).distinct().sorted()
            reminderRepository.saveReminderTimes(updated)
            ReminderScheduler.scheduleAll(getApplication(), updated)
            reminderInput.value = ""
            message.value = resources.getString(R.string.message_reminder_added)
        }
    }

    fun removeReminderTime(value: String) {
        viewModelScope.launch {
            val updated = reminderRepository.readTimesOnce().filterNot { it == value }
            reminderRepository.saveReminderTimes(updated)
            ReminderScheduler.scheduleAll(getApplication(), updated)
            message.value = resources.getString(R.string.message_reminder_removed)
        }
    }

    fun exportCsvFromActivity(onIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            val file = exportManager.exportCsv(db.getAllEntries())
            if (file == null) {
                message.value = resources.getString(R.string.message_export_csv_failed)
            } else {
                launchShareIntent(file, "text/csv", onIntent)
            }
        }
    }

    fun exportSqliteFromActivity(onIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            val file = exportManager.exportSqliteCopy()
            if (file == null) {
                message.value = resources.getString(R.string.message_export_sqlite_failed)
            } else {
                launchShareIntent(file, "application/octet-stream", onIntent)
            }
        }
    }

    private fun launchShareIntent(
        file: File,
        mimeType: String,
        onIntent: (Intent) -> Unit
    ) {
        val intent = buildShareIntent(file, mimeType)
        val context = getApplication<Application>()
        val pm = context.packageManager
        val hasTarget = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        if (!hasTarget) {
            message.value = resources.getString(R.string.message_no_share_app)
            return
        }
        runCatching {
            onIntent(intent)
            message.value = resources.getString(R.string.message_export_started)
        }.onFailure {
            message.value = resources.getString(R.string.message_export_failed)
        }
    }

    private fun buildShareIntent(file: File, mimeType: String): Intent {
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(
                context.contentResolver,
                "export",
                uri
            )
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun reloadEntries() {
        viewModelScope.launch {
            refreshEntries(showError = true)
        }
    }

    fun startEditEntry(entry: TrackerEntry) {
        entryToEdit.value = EditState(
            entryId = entry.id,
            painLevel = entry.painLevel,
            painType = entry.painType,
            mentalState = entry.mentalState,
            activities = entry.activitiesPreviousHours,
            comments = entry.comments
        )
    }

    fun dismissEditEntry() {
        entryToEdit.value = null
    }

    fun updateEditPainLevel(value: PainLevel) {
        val current = entryToEdit.value ?: return
        entryToEdit.value = when (value) {
            PainLevel.NONE -> current.copy(
                painLevel = value,
                painType = PainType.NOT_APPLICABLE
            )
            else -> {
                val nextPainType = if (current.painType == PainType.NOT_APPLICABLE) {
                    PainType.CONTINUOUS
                } else {
                    current.painType
                }
                current.copy(painLevel = value, painType = nextPainType)
            }
        }
    }

    fun updateEditPainType(value: PainType) {
        val current = entryToEdit.value ?: return
        if (current.painLevel == PainLevel.NONE) {
            entryToEdit.value = current.copy(painType = PainType.NOT_APPLICABLE)
            return
        }
        entryToEdit.value = current.copy(painType = value)
    }

    fun updateEditMentalState(value: String) {
        val current = entryToEdit.value ?: return
        entryToEdit.value = current.copy(mentalState = value)
    }

    fun updateEditActivities(value: String) {
        val current = entryToEdit.value ?: return
        entryToEdit.value = current.copy(activities = value)
    }

    fun updateEditComments(value: String) {
        val current = entryToEdit.value ?: return
        entryToEdit.value = current.copy(comments = value)
    }

    fun saveEditedEntry() {
        val current = entryToEdit.value ?: return
        viewModelScope.launch {
            runCatching {
                val finalPainType = if (current.painLevel == PainLevel.NONE) {
                    PainType.NOT_APPLICABLE
                } else {
                    current.painType
                }
                db.updateEntry(
                    id = current.entryId,
                    painLevel = current.painLevel,
                    painType = finalPainType,
                    mentalState = current.mentalState.trim(),
                    activities = current.activities.trim(),
                    comments = current.comments.trim()
                )
            }.onSuccess {
                entryToEdit.value = null
                refreshEntries(showError = false)
                message.value = resources.getString(R.string.message_entry_updated)
            }.onFailure {
                message.value = resources.getString(R.string.message_entry_update_failed)
            }
        }
    }

    fun deleteEntry(entry: TrackerEntry) {
        viewModelScope.launch {
            runCatching {
                db.deleteEntry(entry.id)
            }.onSuccess {
                if (entryToEdit.value?.entryId == entry.id) {
                    entryToEdit.value = null
                }
                refreshEntries(showError = false)
                message.value = resources.getString(R.string.message_entry_deleted)
            }.onFailure {
                message.value = resources.getString(R.string.message_entry_delete_failed)
            }
        }
    }

    fun formatTime(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(timestampFormatter)
    }

    private suspend fun refreshEntries(showError: Boolean) {
        runCatching { db.getAllEntries() }
            .onSuccess { entries.value = it }
            .onFailure {
                if (showError) {
                    message.value = resources.getString(R.string.message_entries_load_failed)
                }
            }
    }

    private fun normalizeReminderInput(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.matches(Regex("""\d{2}:\d{2}"""))) return trimmed
        if (!trimmed.matches(Regex("""\d{4}"""))) return null
        return "${trimmed.substring(0, 2)}:${trimmed.substring(2, 4)}"
    }
}
