package com.paintracker.ui

import com.paintracker.data.TrackerEntry

enum class HistoryCell(val widthDp: Int) {
    TIME(150),
    PAIN_1(90),
    PAIN_2(140),
    MENTAL(220),
    ACTIVITIES(240),
    COMMENTS(240)
}

data class HistoryRow(
    val time: String,
    val pain1: String,
    val pain2: String,
    val mental: String,
    val activities: String,
    val comments: String
)

fun List<TrackerEntry>.toHistoryRows(
    formatTime: (Long) -> String
): List<HistoryRow> {
    return map { entry ->
        HistoryRow(
            time = formatTime(entry.timestampEpochMillis),
            pain1 = entry.painLevel.display,
            pain2 = entry.painType.display,
            mental = entry.mentalState,
            activities = entry.activitiesPreviousHours,
            comments = entry.comments
        )
    }
}
