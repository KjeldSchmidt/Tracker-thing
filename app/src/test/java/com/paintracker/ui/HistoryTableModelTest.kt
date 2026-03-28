package com.paintracker.ui

import com.paintracker.data.PainLevel
import com.paintracker.data.PainType
import com.paintracker.data.TrackerEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTableModelTest {

    private fun sampleEntries(): List<TrackerEntry> {
        return listOf(
            TrackerEntry(
                id = 1L,
                timestampEpochMillis = 1_700_000_000_000L,
                painLevel = PainLevel.NONE,
                painType = PainType.NOT_APPLICABLE,
                mentalState = "Ruhig",
                activitiesPreviousHours = "Lesen",
                comments = "Kein Schmerz"
            ),
            TrackerEntry(
                id = 2L,
                timestampEpochMillis = 1_700_000_100_000L,
                painLevel = PainLevel.MEDIUM,
                painType = PainType.CONTINUOUS,
                mentalState = "Konzentriert",
                activitiesPreviousHours = "Arbeit",
                comments = "Druckgefühl"
            ),
            TrackerEntry(
                id = 3L,
                timestampEpochMillis = 1_700_000_200_000L,
                painLevel = PainLevel.MEDIUM,
                painType = PainType.INTERMITTENT,
                mentalState = "Unruhig",
                activitiesPreviousHours = "Spazieren",
                comments = "Stiche"
            )
        )
    }

    @Test
    fun toHistoryRows_mapsAllFieldsInOrder() {
        val entries = listOf(sampleEntries().last())

        val rows = entries.toHistoryRows { "2026-03-28 19:30" }

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("2026-03-28 19:30", row.time)
        assertEquals("Mittel", row.pain1)
        assertEquals("Intermittierend", row.pain2)
        assertEquals("Unruhig", row.mental)
        assertEquals("Spazieren", row.activities)
        assertEquals("Stiche", row.comments)
    }

    @Test
    fun toHistoryRows_returnsEmptyForEmptyInput() {
        val rows = emptyList<TrackerEntry>().toHistoryRows { "unused" }
        assertEquals(0, rows.size)
    }

    @Test
    fun filterByHistory_filtersByPain1Only() {
        val filtered = sampleEntries().filterByHistory(
            HistoryFilter(painLevel = PainLevel.MEDIUM, painType = null)
        )
        assertEquals(listOf(2L, 3L), filtered.map { it.id })
    }

    @Test
    fun filterByHistory_filtersByPain2Only() {
        val filtered = sampleEntries().filterByHistory(
            HistoryFilter(painLevel = null, painType = PainType.NOT_APPLICABLE)
        )
        assertEquals(listOf(1L), filtered.map { it.id })
    }

    @Test
    fun filterByHistory_appliesPain1AndPain2AsAnd() {
        val filtered = sampleEntries().filterByHistory(
            HistoryFilter(painLevel = PainLevel.MEDIUM, painType = PainType.INTERMITTENT)
        )
        assertEquals(listOf(3L), filtered.map { it.id })
    }

    @Test
    fun filterByHistory_allFiltersReturnsAllEntries() {
        val filtered = sampleEntries().filterByHistory(
            HistoryFilter(painLevel = null, painType = null)
        )
        assertEquals(listOf(1L, 2L, 3L), filtered.map { it.id })
    }
}
