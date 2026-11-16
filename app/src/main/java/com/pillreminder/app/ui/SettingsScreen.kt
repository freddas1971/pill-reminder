package com.pillreminder.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pillreminder.app.AppData
import com.pillreminder.app.Family
import com.pillreminder.app.Notifier
import com.pillreminder.app.Repo
import com.pillreminder.app.Scheduler
import kotlinx.coroutines.launch

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (issues.isEmpty()) {
                Panel {
                    Text("✓ Phone is set up correctly for reminders.", style = MaterialTheme.typography.titleMedium, color = Sage)
                }
            } else {
                SetupIssuesCard(issues)
            }

            FamilySection(data)

            Panel("Reminders") {
                Toggle(
                    "Remind again if not ticked off",
                    "Rings again every ${Scheduler.FOLLOW_UP_MINUTES} minutes (up to ${Scheduler.MAX_FOLLOW_UPS} times) until you press \"I've taken it\".",
                    s.followUps,
                ) { v -> Repo.updateSettings(context) { it.copy(followUps = v) } }
                Spacer(Modifier.height(14.dp))
                Toggle(
                    "Keep ringing until answered",
                    "The sound repeats until you open or answer the reminder. Good if you are hard of hearing or often away from the phone.",
                    s.ringContinuously,
                ) { v -> Repo.updateSettings(context) { it.copy(ringContinuously = v) } }
            }

            Panel("Check the reminder") {
                Text(
                    "Sends a test reminder now so you can hear the sound and see how it looks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                BigButton("Send a test reminder", { Notifier.showTest(context) }, Modifier.fillMaxWidth().padding(top = 10.dp))
                Text(
                    "Tip: to make the reminder louder, raise the phone's ALARM volume (not the ring volume).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FamilySection(data: AppData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = data.settings
    var name by remember { mutableStateOf(s.personName) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    Panel("Family alerts") {
        Toggle(
            "Alert a family member",
            "A son, daughter or carer gets a message on their phone when a pill is missed, when \"Remind me later\" is pressed, and when pills are taken.",
            s.familyEnabled,
        ) { v ->
            if (v) Family.ensureTopic(context)
            Repo.updateSettings(context) { it.copy(familyEnabled = v) }
        }

        if (s.familyEnabled && s.ntfyTopic.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("Your name, as the family knows you", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { v ->
                    name = v
                    Repo.updateSettings(context) { it.copy(personName = v.trim()) }
                },
                placeholder = { Text("e.g. Mom, or Oupa Piet") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            Text("Set up the family member's phone", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Step("1", "On their phone, install the free app \"ntfy\" from the Play Store (or App Store).")
            Step("2", "Open ntfy, tap +, and type this topic:")
            SelectionContainer {
                Text(
                    s.ntfyTopic,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = PrimaryDeep,
                    modifier = Modifier
                        .padding(start = 44.dp, top = 4.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PrimarySoft)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Step("3", "Tap Subscribe. That's it. Use \"Send a test alert\" below to check.")
            Spacer(Modifier.height(4.dp))
            Text(
                "Easiest: tap \"Share setup\" and send the instructions to them on WhatsApp.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )

            Spacer(Modifier.height(12.dp))
            BigButton("Share setup with family", {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, Family.shareText(Repo.get(context).settings))
                context.startActivity(Intent.createChooser(send, "Send setup to…"))
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(
                    if (sending) "Sending…" else "Send a test alert",
                    {
                        sending = true
                        testStatus = null
                        scope.launch {
                            val err = Family.sendTest(context)
                            sending = false
                            testOk = err == null
                            testStatus = err ?: "✓ Sent. Check the family member's phone."
                        }
                    },
                    Modifier.weight(1f),
                    color = Sage,
                    enabled = !sending,
                )
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("ntfy topic", s.ntfyTopic))
                        testOk = true
                        testStatus = "Topic copied."
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.heightIn(min = 60.dp),
                ) { Text("Copy topic", style = MaterialTheme.typography.labelLarge, color = PrimaryDeep) }
            }
            testStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (testOk) Sage else Rose,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "A \"missed\" alert is sent if pills are not ticked off ${Family.MISSED_AFTER_MINUTES} minutes after the reminder. " +
                    "Alerts need internet on this phone; if it is offline, they are sent as soon as it reconnects.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(
            number,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Primary)
                .padding(horizontal = 11.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Panel(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CardWhite)
            .border(1.5.dp, Line, RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.padding(bottom = 12.dp))
        }
        content()
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
            colors = SwitchDefaults.colors(checkedTrackColor = Sage),
        )
    }
}
