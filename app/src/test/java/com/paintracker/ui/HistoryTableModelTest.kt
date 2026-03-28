package com.paintracker.ui

import com.paintracker.data.PainLevel
import com.paintracker.data.PainType
import com.paintracker.data.TrackerEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTableModelTest {

    @Test
    fun toHistoryTableRows_mapsAllFieldsInOrder() {
        val entries = listOf(
            TrackerEntry(
                id = 7L,
                timestampEpochMillis = 1_700_000_000_000L,
                painLevel = PainLevel.MEDIUM,
                painType = PainType.INTERMITTENT,
                mentalState = "Unruhig",
                activitiesPreviousHours = "Spazieren",
                comments = "Abends stärker"
            )
        )

        val rows = toHistoryRowModels(entries) { "2026-03-28 19:30" }

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("2026-03-28 19:30", row.time)
        assertEquals("Mittel", row.pain1)
        assertEquals("Intermittierend", row.pain2)
        assertEquals("Unruhig", row.mental)
        assertEquals("Spazieren", row.activities)
        assertEquals("Abends stärker", row.comments)
    }

    @Test
    fun toHistoryRowModels_returnsEmptyForEmptyInput() {
        val rows = toHistoryRowModels(emptyList()) { "unused" }
        assertEquals(0, rows.size)
    }
}
