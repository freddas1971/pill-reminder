package com.pillreminder.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Simple JSON-file storage, shared by the screens, the alarm receiver and the boot receiver. */
object Repo {
    private const val FILE = "pills.json"
    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> get() = _state
    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        val f = File(context.applicationContext.filesDir, FILE)
        if (f.exists()) {
            runCatching { _state.value = AppData.fromJson(f.readText()) }
        }
        loaded = true
    }

    fun get(context: Context): AppData {
        init(context)
        return _state.value
    }

    @Synchronized
    fun update(context: Context, change: (AppData) -> AppData) {
        init(context)
        val updated = prune(change(_state.value))
        _state.value = updated
        val dir = context.applicationContext.filesDir
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(updated.toJson())
        tmp.renameTo(File(dir, FILE))
    }

    fun saveMed(context: Context, med: Medication) {
        update(context) { d ->
            val others = d.meds.filter { it.id != med.id }
            d.copy(meds = (others + med).sortedBy { it.name.lowercase() })
        }
        Scheduler.reschedule(context)
    }

    fun deleteMed(context: Context, id: String) {
        val med = get(context).meds.firstOrNull { it.id == id }
        update(context) { d -> d.copy(meds = d.meds.filter { it.id != id }) }
        med?.photoPath?.let { File(it).delete() }
        Scheduler.reschedule(context)
    }

    fun markTaken(context: Context, keys: Collection<String>) {
        val now = System.currentTimeMillis()
        update(context) { d -> d.copy(taken = d.taken + keys.associateWith { now }) }
    }

    fun unmarkTaken(context: Context, key: String) {
        update(context) { d -> d.copy(taken = d.taken - key) }
    }

    fun updateSettings(context: Context, change: (Settings) -> Settings) {
        update(context) { d -> d.copy(settings = change(d.settings)) }
    }

    /** Forget "taken" records older than 90 days so the file stays small. */
    private fun prune(d: AppData): AppData {
        val cutoff = LocalDate.now(ZoneId.systemDefault()).minusDays(90).toString()
        val kept = d.taken.filterKeys { k -> (k.split("|").getOrNull(1) ?: "9999") >= cutoff }
        return if (kept.size == d.taken.size) d else d.copy(taken = kept)
    }
}
