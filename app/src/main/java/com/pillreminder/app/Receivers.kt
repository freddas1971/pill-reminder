package com.pillreminder.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        val slot = intent.getLongExtra(Scheduler.EXTRA_SLOT, 0L)
        val keys = intent.getStringArrayExtra(Scheduler.EXTRA_KEYS)?.toList() ?: emptyList()
        when (intent.action) {
            Scheduler.ACTION_DOSE_TIME -> onDoseTime(context, slot)
            Scheduler.ACTION_FOLLOW_UP -> onFollowUp(context, slot, keys, intent.getIntExtra(Scheduler.EXTRA_ATTEMPT, 0))
            Scheduler.ACTION_TAKEN -> Scheduler.takeFromReminder(context, slot, keys)
            Scheduler.ACTION_SNOOZE -> Scheduler.snooze(context, slot, keys)
        }
    }

    private fun onDoseTime(context: Context, slot: Long) {
        val data = Repo.get(context)
        if (slot > 0L) {
            val time = Scheduler.fromMillis(slot)
            val minute = time.hour * 60 + time.minute
            val due = data.dosesAt(time.toLocalDate(), minute).filter { !data.taken.containsKey(it.key) }
            if (due.isNotEmpty()) {
                val keys = due.map { it.key }
                Notifier.show(context, slot, keys, attempt = 0)
                if (data.settings.followUps) {
                    Scheduler.scheduleFollowUp(context, slot, keys, attempt = 1, delayMinutes = Scheduler.FOLLOW_UP_MINUTES)
                }
            }
            Scheduler.reschedule(context, after = time)
        } else {
            Scheduler.reschedule(context)
        }
    }

    private fun onFollowUp(context: Context, slot: Long, keys: List<String>, attempt: Int) {
        val data = Repo.get(context)
        val stillDue = keys.filter { !data.taken.containsKey(it) && data.doseFromKey(it) != null }
        if (stillDue.isEmpty()) return
        Notifier.show(context, slot, stillDue, attempt)
        // attempt 0 means "remind me later" was pressed; automatic follow-ups count 1..MAX
        if (attempt in 1 until Scheduler.MAX_FOLLOW_UPS && data.settings.followUps) {
            Scheduler.scheduleFollowUp(context, slot, stillDue, attempt + 1, Scheduler.FOLLOW_UP_MINUTES)
        }
    }
}

/** Alarms are wiped when the phone restarts or the clock changes, so set them up again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        Notifier.ensureChannel(context)
        Scheduler.reschedule(context)
    }
}
