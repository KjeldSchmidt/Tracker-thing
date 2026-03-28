package com.paintracker.data

import java.time.Instant

enum class PainLevel(val display: String) {
    NONE("None"),
    LIGHT("Light"),
    MEDIUM("Medium"),
    STRONG("Strong");

    companion object {
        fun fromName(value: String): PainLevel = runCatching { valueOf(value) }.getOrDefault(NONE)
    }
}

enum class PainType(val display: String) {
    CONTINUOUS("Continuous"),
    INTERMITTENT("Intermittent");

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
