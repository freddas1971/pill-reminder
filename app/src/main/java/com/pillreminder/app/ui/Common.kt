package com.pillreminder.app.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.pillreminder.app.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppBar(title: String, navigation: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Teal)
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigation != null) navigation() else Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        actions()
    }
}

/** Shows the pill's photo, or a drawn pill if there is no photo. */
@Composable
fun PillPhoto(path: String?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { Photos.load(path, 700)?.asImageBitmap() }
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(TealLight),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = "Pill photo", contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            PillDrawing(Modifier.size(size * 0.6f))
        }
    }
}

@Composable
fun PillDrawing(modifier: Modifier) {
    Canvas(modifier.rotate(-45f)) {
        val h = size.height * 0.42f
        val top = (size.height - h) / 2
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(Color.White, Offset(0f, top), Size(size.width, h), r)
        drawRoundRect(
            Color(0xFFE8963A), Offset(size.width / 2, top), Size(size.width / 2, h), r
        )
        drawRect(Color(0xFFE8963A), Offset(size.width / 2, top), Size(h / 2, h))
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Teal,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------- Phone settings that reminders depend on ----------

data class SetupIssue(val title: String, val detail: String, val buttonText: String, val fix: (Activity) -> Unit)

fun findSetupIssues(context: Context): List<SetupIssue> {
    val issues = mutableListOf<SetupIssue>()

    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        issues += SetupIssue(
            "Notifications are switched off",
            "Without them the app cannot remind you.",
            "Turn on",
        ) { a ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !a.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
                !askedBefore(a)
            ) {
                markAsked(a)
                a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                a.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName)
                )
            }
        }
    }

    val am = context.getSystemService(AlarmManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am != null && !am.canScheduleExactAlarms()) {
        issues += SetupIssue(
            "Exact reminder times are not allowed",
            "Allow \"Alarms & reminders\" so reminders ring on the minute.",
            "Allow",
        ) { a ->
            a.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${a.packageName}"))
            )
        }
    }

    val pm = context.getSystemService(PowerManager::class.java)
    if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
        issues += SetupIssue(
            "Battery saver may block reminders",
            "Let the app run in the background so the phone doesn't silence it.",
            "Allow",
        ) { a ->
            runCatching {
                a.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${a.packageName}"))
                )
            }.onFailure {
                a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    val nm = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 34 && nm != null && !nm.canUseFullScreenIntent()) {
        issues += SetupIssue(
            "Reminders can't show on the lock screen",
            "Allow full-screen reminders so the pill photo appears even when the phone is locked.",
            "Allow",
        ) { a ->
            runCatching {
                a.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${a.packageName}"))
                )
            }
        }
    }
    return issues
}

private fun askedBefore(c: Context) =
    c.getSharedPreferences("setup", Context.MODE_PRIVATE).getBoolean("asked_notif", false)

private fun markAsked(c: Context) =
    c.getSharedPreferences("setup", Context.MODE_PRIVATE).edit().putBoolean("asked_notif", true).apply()

@Composable
fun SetupIssuesCard(issues: List<SetupIssue>, modifier: Modifier = Modifier) {
    if (issues.isEmpty()) return
    val activity = LocalContext.current as? Activity
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AmberLight),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Amber)
                Spacer(Modifier.width(8.dp))
                Text("Phone setup needed", style = MaterialTheme.typography.titleMedium, color = Amber)
            }
            issues.forEach { issue ->
                Column {
                    Text(issue.title, style = MaterialTheme.typography.bodyLarge)
                    Text(issue.detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    BigButton(issue.buttonText, onClick = { activity?.let { issue.fix(it) } }, color = Amber)
                }
            }
        }
    }
}
