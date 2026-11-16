package com.pillreminder.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object Fmt {
    fun time(context: Context, minute: Int): String {
        val t = LocalTime.of(minute / 60, minute % 60)
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return t.format(DateTimeFormatter.ofPattern(pattern))
    }

    fun day(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
    fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
}

object Notifier {
    private const val CHANNEL = "pill_reminders_v1"
    private const val TEST_ID = 3

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(CHANNEL, "Remi pill reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when it is time to take a pill"
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun show(context: Context, slot: Long, keys: List<String>, attempt: Int) {
        val data = Repo.get(context)
        val doses = keys.mapNotNull { data.doseFromKey(it) }
        if (doses.isEmpty()) return
        ensureChannel(context)

        val id = Scheduler.slotId(slot)
        val timeText = Fmt.time(context, doses.first().minute)
        val title = when {
            attempt > 0 -> "Remi here again: $timeText pills not taken yet"
            doses.size == 1 -> "Time to take ${doses[0].med.name}"
            else -> "Time for your ${doses.size} pills ($timeText)"
        }
        val lines = doses.map { d ->
            if (d.med.instructions.isBlank()) d.med.name else "${d.med.name}: ${d.med.instructions}"
        }

        val keyArray = keys.toTypedArray()
        val openIntent = Intent(context, TakeActivity::class.java)
            .putExtra(Scheduler.EXTRA_SLOT, slot)
            .putExtra(Scheduler.EXTRA_KEYS, keyArray)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPi = PendingIntent.getActivity(
            context, id, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val takenPi = broadcast(context, id, Scheduler.ACTION_TAKEN, slot, keyArray)
        val snoozePi = broadcast(context, id, Scheduler.ACTION_SNOOZE, slot, keyArray)

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(0xFFC0613F.toInt())
            .setContentTitle(title)
            .setContentText(lines.joinToString(" • "))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .setAutoCancel(false)
            .addAction(0, "✓ I've taken it", takenPi)
            .addAction(0, "Remind me in ${Scheduler.SNOOZE_MINUTES} min", snoozePi)

        val picture = Photos.collage(doses.mapNotNull { it.med.photoPath })
        if (picture != null) {
            builder.setLargeIcon(Photos.load(doses.firstNotNullOfOrNull { it.med.photoPath }, 256))
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .setSummaryText(lines.joinToString(" • "))
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
        }

        val notification = builder.build()
        if (data.settings.ringContinuously) {
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }
        post(context, id, notification)
    }

    /** A sample reminder so the user can hear the sound and see how it looks. */
    fun showTest(context: Context) {
        ensureChannel(context)
        val data = Repo.get(context)
        val med = data.meds.firstOrNull()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(0xFFC0613F.toInt())
            .setContentTitle("Hi, it's Remi! This is how a reminder looks")
            .setContentText(if (med != null) "Time to take ${med.name}" else "Your pill's name and photo will show here")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, TEST_ID, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
            )
        Photos.load(med?.photoPath, 1000)?.let {
            builder.setLargeIcon(Photos.load(med?.photoPath, 256))
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(it))
        }
        post(context, TEST_ID, builder.build())
    }

    fun cancel(context: Context, slot: Long) {
        NotificationManagerCompat.from(context).cancel(Scheduler.slotId(slot))
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun broadcast(context: Context, id: Int, action: String, slot: Long, keys: Array<String>): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(action)
            .putExtra(Scheduler.EXTRA_SLOT, slot)
            .putExtra(Scheduler.EXTRA_KEYS, keys)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
