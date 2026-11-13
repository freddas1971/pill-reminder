package com.pillreminder.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pillreminder.app.AppData
import com.pillreminder.app.Notifier
import com.pillreminder.app.Repo
import com.pillreminder.app.Scheduler

@Composable
fun SettingsScreen(data: AppData, issues: List<SetupIssue>, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = data.settings
    Column(Modifier.fillMaxSize()) {
        AppBar("Settings", navigation = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (issues.isEmpty()) {
                Text("✓ Phone is set up correctly for reminders.", style = MaterialTheme.typography.titleMedium, color = Green)
            } else {
                SetupIssuesCard(issues)
            }

            Toggle(
                "Remind again if not ticked off",
                "Rings again every ${Scheduler.FOLLOW_UP_MINUTES} minutes (up to ${Scheduler.MAX_FOLLOW_UPS} times) until you press \"I've taken it\".",
                s.followUps,
            ) { v -> Repo.updateSettings(context) { it.copy(followUps = v) } }

            Toggle(
                "Keep ringing until answered",
                "The sound repeats until you open or answer the reminder. Good if you are hard of hearing or often away from the phone.",
                s.ringContinuously,
            ) { v -> Repo.updateSettings(context) { it.copy(ringContinuously = v) } }

            Column {
                Text("Check the reminder", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sends a test reminder now so you can hear the sound and see how it looks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.width(8.dp))
                BigButton("Send a test reminder", { Notifier.showTest(context) }, Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            Text(
                "Tip: to make the reminder louder, raise the phone's ALARM volume (not the ring volume).",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Teal),
        )
    }
}
