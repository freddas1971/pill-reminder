package com.pillreminder.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Family alerts: sends push messages to a second person's phone through ntfy (https://ntfy.sh).
 * The second person installs the free ntfy app and subscribes to this phone's private topic.
 */
object Family {
    const val MISSED_AFTER_MINUTES = 30L

    private const val PRIORITY_LOW = 3
    private const val PRIORITY_HIGH = 4
    private const val PRIORITY_URGENT = 5

    /** Returns this phone's topic, creating a hard-to-guess one the first time. */
    fun ensureTopic(context: Context): String {
        val existing = Repo.get(context).settings.ntfyTopic
        if (existing.isNotBlank()) return existing
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        val topic = "pills-" + (1..12).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        Repo.updateSettings(context) { it.copy(ntfyTopic = topic) }
        return topic
    }

    fun topicLink(s: Settings): String = "${s.ntfyServer.trimEnd('/')}/${s.ntfyTopic}"

    fun shareText(s: Settings): String {
        val who = s.personName.ifBlank { "I" }
        val intro = if (s.personName.isBlank()) "I've set up Pill Reminder" else "${s.personName}'s Pill Reminder is set up"
        return """
            |Hi! $intro so you get a message if a pill is missed.
            |
            |1. Install the free app "ntfy" from the Play Store (or App Store on iPhone).
            |2. Open it, tap +, and subscribe to this topic:
            |   ${s.ntfyTopic}
            |   (leave "Use another server" switched off)
            |
            |Or simply open this link on your phone: ${topicLink(s)}
            |
            |You'll be told when ${if (who == "I") "I take my" else "$who takes their"} pills, asks to be reminded later, or misses a dose.
        """.trimMargin()
    }

    private fun name(s: Settings) = s.personName.ifBlank { "Your family member" }

    private fun pillLines(doses: List<Dose>) = doses.joinToString("\n") { d ->
        "• " + d.med.name + if (d.med.instructions.isBlank()) "" else " (${d.med.instructions})"
    }

    private fun when_(context: Context, d: Dose): String {
        val t = Fmt.time(context, d.minute)
        val today = LocalDate.now()
        return when (d.date) {
            today -> t
            today.minusDays(1) -> "$t yesterday"
            else -> "$t on ${Fmt.shortDate(d.date)}"
        }
    }

    // ---------------- Events ----------------

    fun missed(context: Context, keys: List<String>) {
        val data = Repo.get(context)
        val doses = keys.mapNotNull { data.doseFromKey(it) }.filter { !data.taken.containsKey(it.key) }
        if (doses.isEmpty()) return
        val s = data.settings
        enqueue(
            context,
            title = "${name(s)} has NOT taken the ${when_(context, doses[0])} pills",
            message = "Still not ticked off $MISSED_AFTER_MINUTES minutes after the reminder:\n${pillLines(doses)}\n\nPerhaps give them a call.",
            priority = PRIORITY_URGENT,
            tags = listOf("warning", "pill"),
        )
    }

    fun snoozed(context: Context, keys: List<String>) {
        val data = Repo.get(context)
        val doses = keys.mapNotNull { data.doseFromKey(it) }
        if (doses.isEmpty()) return
        val s = data.settings
        val again = LocalDateTime.now().plusMinutes(Scheduler.SNOOZE_MINUTES)
        enqueue(
            context,
            title = "${name(s)} chose \"Remind me in ${Scheduler.SNOOZE_MINUTES} min\"",
            message = "For the ${when_(context, doses[0])} pills:\n${pillLines(doses)}\n\nThe phone will ring again at ${Fmt.time(context, again.hour * 60 + again.minute)}.",
            priority = PRIORITY_HIGH,
            tags = listOf("alarm_clock"),
        )
    }

    fun taken(context: Context, keys: List<String>) {
        val data = Repo.get(context)
        val doses = keys.mapNotNull { data.doseFromKey(it) }
            .filter { !it.date.isBefore(LocalDate.now().minusDays(1)) } // ignore ticking off old days
        if (doses.isEmpty()) return
        val s = data.settings
        val now = LocalDateTime.now()
        enqueue(
            context,
            title = "${name(s)} took the ${when_(context, doses[0])} pills",
            message = "${pillLines(doses)}\n\nTicked off at ${Fmt.time(context, now.hour * 60 + now.minute)}.",
            priority = PRIORITY_LOW,
            tags = listOf("white_check_mark"),
        )
    }

    // ---------------- Sending ----------------

    private fun enqueue(context: Context, title: String, message: String, priority: Int, tags: List<String>) {
        val s = Repo.get(context).settings
        if (!s.familyEnabled || s.ntfyTopic.isBlank()) return
        val input = Data.Builder()
            .putString("server", s.ntfyServer)
            .putString("topic", s.ntfyTopic)
            .putString("title", title)
            .putString("message", message)
            .putInt("priority", priority)
            .putStringArray("tags", tags.toTypedArray())
            .putLong("created", System.currentTimeMillis())
            .build()
        val request = OneTimeWorkRequestBuilder<FamilyWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /** Sends straight away (used by the "Send test alert" button). Returns null on success, or an error text. */
    suspend fun sendTest(context: Context): String? = withContext(Dispatchers.IO) {
        val s = Repo.get(context).settings
        if (s.ntfyTopic.isBlank()) return@withContext "No topic set up yet."
        runCatching {
            post(
                s.ntfyServer, s.ntfyTopic,
                title = "Test from ${name(s)}'s Pill Reminder",
                message = "If you can see this, family alerts are working.",
                priority = PRIORITY_HIGH,
                tags = listOf("tada"),
            )
        }.exceptionOrNull()?.let { "Could not send: ${it.message ?: "no internet?"}" }
    }

    /** Posts one message to ntfy. Throws on failure. */
    fun post(server: String, topic: String, title: String, message: String, priority: Int, tags: List<String>) {
        val body = JSONObject().apply {
            put("topic", topic)
            put("title", title)
            put("message", message)
            put("priority", priority)
            put("tags", JSONArray(tags))
        }.toString().toByteArray(Charsets.UTF_8)

        val conn = URL(server.trimEnd('/')).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("server answered $code")
        } finally {
            conn.disconnect()
        }
    }
}

/** Delivers one family alert, retrying until the phone has internet again. */
class FamilyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val d = inputData
        val created = d.getLong("created", 0L)
        // Don't deliver stale news: after 12 hours the alert is no longer useful.
        if (System.currentTimeMillis() - created > TimeUnit.HOURS.toMillis(12)) return@withContext Result.failure()
        try {
            Family.post(
                server = d.getString("server") ?: "https://ntfy.sh",
                topic = d.getString("topic") ?: return@withContext Result.failure(),
                title = d.getString("title") ?: "",
                message = d.getString("message") ?: "",
                priority = d.getInt("priority", 3),
                tags = d.getStringArray("tags")?.toList() ?: emptyList(),
            )
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount >= 25) Result.failure() else Result.retry()
        }
    }
}
