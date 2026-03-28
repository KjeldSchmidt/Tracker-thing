package com.paintracker.data

import java.time.Instant

enum class PainLevel(val display: String) {
    NONE("Kein"),
    LIGHT("Leicht"),
    MEDIUM("Mittel"),
    STRONG("Stark");

    companion object {
        fun fromName(value: String): PainLevel = runCatching { valueOf(value) }.getOrDefault(NONE)
    }
}

enum class PainType(val display: String) {
    NOT_APPLICABLE("Nicht zutreffend"),
    CONTINUOUS("Kontinuierlich"),
    INTERMITTENT("Intermittierend");

    companion object {
        fun fromName(value: String): PainType = runCatching { valueOf(value) }.getOrDefault(CONTINUOUS)
    }
}

data class TrackerEntry(
    val id: Long = 0L,
    val timestampEpochMillis: Long = Instant.now().toEpochMilli(),
    val painLevel: PainLevel,
    val painType: PainType,
    val mentalState: String,
    val activitiesPreviousHours: String,
    val comments: String
)
