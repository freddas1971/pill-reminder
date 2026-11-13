package com.pillreminder.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class Frequency { DAILY, WEEKDAYS, EVERY_N_DAYS }

data class Medication(
    val id: String,
    val name: String,
    val instructions: String,
    val photoPath: String?,
    /** Times of day as minutes after midnight, sorted. */
    val times: List<Int>,
    val frequency: Frequency,
    /** 1 = Monday ... 7 = Sunday (java.time.DayOfWeek values). */
    val weekdays: Set<Int>,
    val everyNDays: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
) {
    fun isDueOn(date: LocalDate): Boolean {
        if (date.isBefore(startDate)) return false
        if (endDate != null && date.isAfter(endDate)) return false
        return when (frequency) {
            Frequency.DAILY -> true
            Frequency.WEEKDAYS -> weekdays.contains(date.dayOfWeek.value)
            Frequency.EVERY_N_DAYS -> {
                val n = everyNDays.coerceAtLeast(1)
                ChronoUnit.DAYS.between(startDate, date) % n == 0L
            }
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("instructions", instructions)
        put("photoPath", photoPath ?: JSONObject.NULL)
        put("times", JSONArray(times))
        put("frequency", frequency.name)
        put("weekdays", JSONArray(weekdays.sorted()))
        put("everyNDays", everyNDays)
        put("startDate", startDate.toString())
        put("endDate", endDate?.toString() ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject): Medication {
            val times = o.getJSONArray("times").let { a -> (0 until a.length()).map { a.getInt(it) } }
            val days = o.optJSONArray("weekdays")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
            return Medication(
                id = o.getString("id"),
                name = o.getString("name"),
                instructions = o.optString("instructions", ""),
                photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath"),
                times = times.sorted(),
                frequency = runCatching { Frequency.valueOf(o.getString("frequency")) }.getOrDefault(Frequency.DAILY),
                weekdays = days,
                everyNDays = o.optInt("everyNDays", 2),
                startDate = LocalDate.parse(o.getString("startDate")),
                endDate = if (o.isNull("endDate")) null else LocalDate.parse(o.getString("endDate")),
            )
        }
    }
}

/** One scheduled dose of one medication. */
data class Dose(val med: Medication, val date: LocalDate, val minute: Int) {
    val key: String get() = doseKey(med.id, date, minute)
    val dateTime: LocalDateTime get() = LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60))
}

fun doseKey(medId: String, date: LocalDate, minute: Int) = "$medId|$date|$minute"

data class Settings(
    val followUps: Boolean = true,
    val ringContinuously: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("followUps", followUps)
        put("ringContinuously", ringContinuously)
    }

    companion object {
        fun fromJson(o: JSONObject?): Settings = if (o == null) Settings() else Settings(
            followUps = o.optBoolean("followUps", true),
            ringContinuously = o.optBoolean("ringContinuously", false),
        )
    }
}

data class AppData(
    val meds: List<Medication> = emptyList(),
    /** dose key -> epoch millis when marked taken */
    val taken: Map<String, Long> = emptyMap(),
    val settings: Settings = Settings(),
) {
    fun dosesOn(date: LocalDate): List<Dose> =
        meds.filter { it.isDueOn(date) }
            .flatMap { m -> m.times.map { Dose(m, date, it) } }
            .sortedWith(compareBy({ it.minute }, { it.med.name.lowercase() }))

    fun dosesAt(date: LocalDate, minute: Int): List<Dose> = dosesOn(date).filter { it.minute == minute }

    fun doseFromKey(key: String): Dose? {
        val parts = key.split("|")
        if (parts.size != 3) return null
        val med = meds.firstOrNull { it.id == parts[0] } ?: return null
        val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
        val minute = parts[2].toIntOrNull() ?: return null
        return Dose(med, date, minute)
    }

    /** The earliest dose time strictly after [after], or null if nothing is scheduled. */
    fun nextDoseTime(after: LocalDateTime): LocalDateTime? {
        var date = after.toLocalDate()
        repeat(400) {
            val candidates = meds.filter { it.isDueOn(date) }
                .flatMap { it.times }
                .map { LocalDateTime.of(date, LocalTime.of(it / 60, it % 60)) }
                .filter { it.isAfter(after) }
            if (candidates.isNotEmpty()) return candidates.min()
            date = date.plusDays(1)
        }
        return null
    }

    fun toJson(): String = JSONObject().apply {
        put("version", 1)
        put("meds", JSONArray().apply { meds.forEach { put(it.toJson()) } })
        put("taken", JSONObject().apply { taken.forEach { (k, v) -> put(k, v) } })
        put("settings", settings.toJson())
    }.toString()

    companion object {
        fun fromJson(text: String): AppData {
            val o = JSONObject(text)
            val meds = o.optJSONArray("meds")?.let { a ->
                (0 until a.length()).mapNotNull { runCatching { Medication.fromJson(a.getJSONObject(it)) }.getOrNull() }
            } ?: emptyList()
            val taken = mutableMapOf<String, Long>()
            o.optJSONObject("taken")?.let { t -> t.keys().forEach { k -> taken[k] = t.getLong(k) } }
            return AppData(meds, taken, Settings.fromJson(o.optJSONObject("settings")))
        }
    }
}
