package com.paintracker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.paintracker.R
import com.paintracker.data.PainLevel
import com.paintracker.data.PainType
import com.paintracker.data.TrackerEntry
import com.paintracker.ui.AppViewModel
import com.paintracker.ui.UiState
import com.paintracker.ui.theme.PainTrackerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        viewModel.ensureReminderSchedule()

        setContent {
            PainTrackerTheme {
                AppRoot(viewModel)
            }
        }
    }
}

private enum class RootTab {
    ENTRY,
    HISTORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(viewModel: AppViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(RootTab.ENTRY) }
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.title_app)) })
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                RootTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab.ordinal == index,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                when (tab) {
                                    RootTab.ENTRY -> stringResource(R.string.tab_new_entry)
                                    RootTab.HISTORY -> stringResource(R.string.tab_history)
                                }
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                RootTab.ENTRY -> EntryTab(
                    state = state,
                    onPainLevelChanged = viewModel::updatePainLevel,
                    onPainTypeChanged = viewModel::updatePainType,
                    onMentalChanged = viewModel::updateMentalState,
                    onActivitiesChanged = viewModel::updateActivities,
                    onCommentsChanged = viewModel::updateComments,
                    onSaveEntry = viewModel::saveEntry,
                    onReminderInputChanged = viewModel::updateReminderInput,
                    onAddReminder = viewModel::addReminderTime,
                    onRemoveReminder = viewModel::removeReminderTime,
                    onExportCsv = viewModel::exportCsvFromActivity,
                    onExportSqlite = viewModel::exportSqliteFromActivity
                )

                RootTab.HISTORY -> {
                    LaunchedEffect(selectedTab) {
                        viewModel.reloadEntries()
                    }
                    HistoryTab(entries = state.entries, formatTime = viewModel::formatTime)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EntryTab(
    state: UiState,
    onPainLevelChanged: (PainLevel) -> Unit,
    onPainTypeChanged: (PainType) -> Unit,
    onMentalChanged: (String) -> Unit,
    onActivitiesChanged: (String) -> Unit,
    onCommentsChanged: (String) -> Unit,
    onSaveEntry: () -> Unit,
    onReminderInputChanged: (String) -> Unit,
    onAddReminder: () -> Unit,
    onRemoveReminder: (String) -> Unit,
    onExportCsv: ((Intent) -> Unit) -> Unit,
    onExportSqlite: ((Intent) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val launchShareChooser: (Intent) -> Unit = { shareIntent ->
        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.export_chooser_title))
        runCatching {
            context.startActivity(chooser)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.label_time_auto_now), style = MaterialTheme.typography.titleMedium)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.label_pain_1), style = MaterialTheme.typography.titleSmall)
                EnumDropdown(
                    label = stringResource(R.string.label_pain_intensity),
                    selectedText = state.form.painLevel.display,
                    options = PainLevel.entries.map { it.display to it },
                    onSelect = onPainLevelChanged
                )

                Text(stringResource(R.string.label_pain_2), style = MaterialTheme.typography.titleSmall)
                EnumDropdown(
                    label = stringResource(R.string.label_pain_type),
                    selectedText = state.form.painType.display,
                    options = painTypeOptions(state.form.painLevel),
                    onSelect = onPainTypeChanged
                )

                OutlinedTextField(
                    value = state.form.mentalState,
                    onValueChange = onMentalChanged,
                    label = { Text(stringResource(R.string.label_mental_state)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                OutlinedTextField(
                    value = state.form.activities,
                    onValueChange = onActivitiesChanged,
                    label = { Text(stringResource(R.string.label_previous_activities)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                OutlinedTextField(
                    value = state.form.comments,
                    onValueChange = onCommentsChanged,
                    label = { Text(stringResource(R.string.label_comments)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Button(onClick = onSaveEntry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_save_entry))
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.section_reminders), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.reminderInput,
                        onValueChange = onReminderInputChanged,
                        label = { Text(stringResource(R.string.label_reminder_time_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onAddReminder) { Text(stringResource(R.string.action_add)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.reminderTimes.forEach { time ->
                        AssistChip(
                            onClick = {},
                            label = { Text(time) },
                            trailingIcon = {
                                TextButton(onClick = { onRemoveReminder(time) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_remove))
                                }
                            }
                        )
                    }
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.section_export), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onExportCsv(launchShareChooser) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_export_csv)) }
                    Button(
                        onClick = { onExportSqlite(launchShareChooser) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_export_sqlite)) }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun HistoryTab(
    entries: List<TrackerEntry>,
    formatTime: (Long) -> String
) {
    if (entries.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.empty_entries))
        }
        return
    }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(verticalScroll)
            .horizontalScroll(horizontalScroll)
    ) {
        TableHeader()
        Divider()
        entries.forEach { entry ->
            TableRow(entry = entry, formatTime = formatTime)
            Divider()
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Cell(stringResource(R.string.table_time), 1.4f, true)
        Cell(stringResource(R.string.table_pain_1), 0.8f, true)
        Cell(stringResource(R.string.table_pain_2), 1.0f, true)
        Cell(stringResource(R.string.table_mental), 1.5f, true)
        Cell(stringResource(R.string.table_activities), 1.5f, true)
        Cell(stringResource(R.string.table_comments), 1.5f, true)
    }
}

@Composable
private fun TableRow(entry: TrackerEntry, formatTime: (Long) -> String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Cell(formatTime(entry.timestampEpochMillis), 1.4f, false)
        Cell(entry.painLevel.display, 0.8f, false)
        Cell(entry.painType.display, 1.0f, false)
        Cell(entry.mentalState, 1.5f, false)
        Cell(entry.activitiesPreviousHours, 1.5f, false)
        Cell(entry.comments, 1.5f, false)
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, isHeader: Boolean) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 4.dp),
        style = if (isHeader) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
        maxLines = 4
    )
}

private fun painTypeOptions(level: PainLevel): List<Pair<String, PainType>> {
    return if (level == PainLevel.NONE) {
        listOf(PainType.NOT_APPLICABLE.display to PainType.NOT_APPLICABLE)
    } else {
        listOf(
            PainType.CONTINUOUS.display to PainType.CONTINUOUS,
            PainType.INTERMITTENT.display to PainType.INTERMITTENT
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selectedText: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (labelText, value) ->
                DropdownMenuItem(
                    text = { Text(labelText) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
