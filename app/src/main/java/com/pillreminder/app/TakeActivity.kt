package com.pillreminder.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pillreminder.app.ui.BigButton
import com.pillreminder.app.ui.Green
import com.pillreminder.app.ui.Muted
import com.pillreminder.app.ui.PillPhoto
import com.pillreminder.app.ui.PillTheme
import com.pillreminder.app.ui.Teal
import com.pillreminder.app.ui.TealLight

/** The big "time to take your pills" screen, shown from the reminder (also over the lock screen). */
class TakeActivity : ComponentActivity() {
    private var slot by mutableStateOf(0L)
    private var keys by mutableStateOf(emptyList<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Repo.init(this)
        readIntent(intent)

        setContent {
            PillTheme {
                val data by Repo.state.collectAsState()
                val doses = keys.mapNotNull { data.doseFromKey(it) }
                val open = doses.filter { !data.taken.containsKey(it.key) }

                Column(
                    Modifier
                        .fillMaxSize()
                        .background(TealLight)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (doses.isEmpty() || open.isEmpty()) {
                        Spacer(Modifier.height(60.dp))
                        Text("✓", style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize * 3), color = Green)
                        Text("All done. Well done!", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        BigButton("Close", { finish() }, Modifier.fillMaxWidth())
                        BigButton("Open the app", {
                            startActivity(Intent(this@TakeActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        }, Modifier.fillMaxWidth(), color = Color.White, contentColor = Teal)
                    } else {
                        Text(
                            "Time to take",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Muted,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            Fmt.time(this@TakeActivity, open.first().minute),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Teal,
                        )
                        open.forEach { d ->
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                            ) {
                                if (open.size == 1) {
                                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        PillPhoto(d.med.photoPath, 240.dp, corner = 20.dp)
                                        Spacer(Modifier.height(12.dp))
                                        Text(d.med.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                                        if (d.med.instructions.isNotBlank()) {
                                            Text(d.med.instructions, style = MaterialTheme.typography.titleMedium, color = Muted, textAlign = TextAlign.Center)
                                        }
                                    }
                                } else {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        PillPhoto(d.med.photoPath, 110.dp)
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(d.med.name, style = MaterialTheme.typography.titleLarge)
                                            if (d.med.instructions.isNotBlank()) {
                                                Text(d.med.instructions, style = MaterialTheme.typography.bodyLarge, color = Muted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        BigButton(
                            if (open.size == 1) "✓  I've taken it" else "✓  I've taken them all",
                            {
                                Scheduler.takeFromReminder(this@TakeActivity, slot, open.map { it.key })
                                finish()
                            },
                            Modifier.fillMaxWidth().height(72.dp),
                            color = Green,
                        )
                        BigButton(
                            "Remind me in ${Scheduler.SNOOZE_MINUTES} minutes",
                            {
                                Scheduler.snooze(this@TakeActivity, slot, open.map { it.key })
                                finish()
                            },
                            Modifier.fillMaxWidth(),
                            color = Color.White,
                            contentColor = Teal,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        slot = intent?.getLongExtra(Scheduler.EXTRA_SLOT, 0L) ?: 0L
        keys = intent?.getStringArrayExtra(Scheduler.EXTRA_KEYS)?.toList() ?: emptyList()
    }
}
