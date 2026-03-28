package com.paintracker.export

import android.content.Context
import com.paintracker.data.TrackerEntry
import com.paintracker.data.TrackerDatabaseHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ExportManager(private val context: Context) {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    fun exportCsv(entries: List<TrackerEntry>): File? {
        val outFile = File(context.cacheDir, "pain_tracker_export.csv")
        return runCatching {
            outFile.bufferedWriter().use { writer ->
                writer.appendLine(
                    "id,time,pain_level,pain_type,mental_state,activities_previous_hours,comments"
                )
                entries.forEach { entry ->
                    writer.appendLine(
                        listOf(
                            entry.id.toString(),
                            formatter.format(Instant.ofEpochMilli(entry.timestampEpochMillis)),
                            entry.painLevel.name,
                            entry.painType.name,
                            csvEscape(entry.mentalState),
                            csvEscape(entry.activitiesPreviousHours),
                            csvEscape(entry.comments)
                        ).joinToString(",")
                    )
                }
            }
            outFile
        }.getOrNull()
    }

    fun exportSqliteCopy(): File? {
        val source = context.getDatabasePath(TrackerDatabaseHelper.DB_NAME)
        if (!source.exists()) return null
        val outFile = File(context.cacheDir, TrackerDatabaseHelper.DB_NAME)
        return runCatching {
            FileInputStream(source).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile
        }.getOrNull()
    }

    private fun csvEscape(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
