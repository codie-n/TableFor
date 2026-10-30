package com.metrolist.tablefor

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Keys for saved availability slots: `yyyy-MM-dd_HH:mm`, an ISO date and a
 * 24-hour local time, e.g. `2026-10-30_19:00`.
 */
object AvailabilityKeys {
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun key(date: LocalDate, time: LocalTime): String = "${date}_${time.format(TIME_FORMAT)}"

    /** The date and time in [key], or null for an old-format (`0_7:00 PM`) or malformed key. */
    fun parse(key: String): Pair<LocalDate, LocalTime>? {
        val parts = key.split("_")
        if (parts.size != 2) return null
        return try {
            LocalDate.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE) to LocalTime.parse(parts[1], TIME_FORMAT)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** [slots] without old-format keys and without dates before [today]. */
    fun prune(slots: Map<String, Boolean>, today: LocalDate): Map<String, Boolean> =
        slots.filterKeys { key -> parse(key)?.first?.isBefore(today) == false }

    /** The keys [prune] would drop: old-format keys and dates before [today]. */
    fun staleKeys(slots: Map<String, Boolean>, today: LocalDate): Set<String> =
        slots.keys - prune(slots, today).keys
}
