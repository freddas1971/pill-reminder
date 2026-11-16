package com.pillreminder.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object Scheduler {
    const val ACTION_DOSE_TIME = "com.pillreminder.app.DOSE_TIME"
    const val ACTION_FOLLOW_UP = "com.pillreminder.app.FOLLOW_UP"
    const val ACTION_TAKEN = "com.pillreminder.app.TAKEN"
    const val ACTION_SNOOZE = "com.pillreminder.app.SNOOZE"
    const val ACTION_MISSED_CHECK = "com.pillreminder.app.MISSED_CHECK"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_KEYS = "keys"
    const val EXTRA_ATTEMPT = "attempt"

    const val FOLLOW_UP_MINUTES = 10L
    const val SNOOZE_MINUTES = 10L
    const val MAX_FOLLOW_UPS = 3

    private const val MAIN_REQUEST = 1

    fun toMillis(t: LocalDateTime): Long = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun fromMillis(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    /** A stable small number for one dose time, used for notification ids and follow-up alarms. */
    fun slotId(slotMillis: Long): Int = ((slotMillis / 60_000L) % 1_000_000L).toInt() + 10

    private fun pendingFlags(base: Int = 0) = base or PendingIntent.FLAG_IMMUTABLE

    /** Schedule the alarm for the next upcoming dose time (replacing any earlier one). */
    fun reschedule(context: Context, after: LocalDateTime = LocalDateTime.now()) {
        val data = Repo.get(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_DOSE_TIME)

        PendingIntent.getBroadcast(context, MAIN_REQUEST, intent, pendingFlags(PendingIntent.FLAG_NO_CREATE))
            ?.let { am.cancel(it); it.cancel() }

        val next = data.nextDoseTime(maxOf(after, LocalDateTime.now())) ?: return
        val at = toMillis(next)
        intent.putExtra(EXTRA_SLOT, at)
        val pi = PendingIntent.getBroadcast(context, MAIN_REQUEST, intent, pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), pendingFlags()
        )
        try {
            if (canExact(am)) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Ring again for these doses later (used for automatic follow-ups and "remind me later"). */
    fun scheduleFollowUp(context: Context, slot: Long, keys: List<String>, attempt: Int, delayMinutes: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FOLLOW_UP)
            .putExtra(EXTRA_SLOT, slot)
            .putExtra(EXTRA_KEYS, keys.toTypedArray())
            .putExtra(EXTRA_ATTEMPT, attempt)
        val pi = PendingIntent.getBroadcast(context, slotId(slot), intent, pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
        val at = System.currentTimeMillis() + delayMinutes * 60_000L
        try {
            if (canExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancelFollowUp(context: Context, slot: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_FOLLOW_UP)
        PendingIntent.getBroadcast(context, slotId(slot), intent, pendingFlags(PendingIntent.FLAG_NO_CREATE))
            ?.let { am.cancel(it); it.cancel() }
    }

    fun canExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    /** Mark doses as taken from anywhere (notification button, alarm screen) and stop reminders for them. */
    fun takeFromReminder(context: Context, slot: Long, keys: List<String>) {
        val before = Repo.get(context).taken
        val newlyTaken = keys.filter { !before.containsKey(it) }
        Repo.markTaken(context, keys)
        cancelFollowUp(context, slot)
        Notifier.cancel(context, slot)
        Family.taken(context, newlyTaken)
    }

    /** "I've taken it" pressed on the Today screen. */
    fun tickInApp(context: Context, dose: Dose) {
        if (Repo.get(context).taken.containsKey(dose.key)) return
        Repo.markTaken(context, listOf(dose.key))
        afterTickedInApp(context, dose)
        Family.taken(context, listOf(dose.key))
    }

    /** Ticked off inside the app: if everything for that time is done, silence its reminder. */
    fun afterTickedInApp(context: Context, dose: Dose) {
        val data = Repo.get(context)
        val allDone = data.dosesAt(dose.date, dose.minute).all { data.taken.containsKey(it.key) }
        if (allDone) {
            val slot = toMillis(dose.dateTime)
            cancelFollowUp(context, slot)
            Notifier.cancel(context, slot)
        }
    }

    fun snooze(context: Context, slot: Long, keys: List<String>) {
        Notifier.cancel(context, slot)
        scheduleFollowUp(context, slot, keys, attempt = 0, delayMinutes = SNOOZE_MINUTES)
        Family.snoozed(context, keys)
    }

    /** Half an hour after the reminder: tell the family if the doses are still not ticked off. */
    fun scheduleMissedCheck(context: Context, slot: Long, keys: List<String>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_MISSED_CHECK)
            .putExtra(EXTRA_SLOT, slot)
            .putExtra(EXTRA_KEYS, keys.toTypedArray())
        val pi = PendingIntent.getBroadcast(context, slotId(slot), intent, pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
        val at = slot + Family.MISSED_AFTER_MINUTES * 60_000L
        try {
            if (canExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
