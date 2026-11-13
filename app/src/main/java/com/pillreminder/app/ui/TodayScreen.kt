package com.pillreminder.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pillreminder.app.AppData
import com.pillreminder.app.Dose
import com.pillreminder.app.Fmt
import com.pillreminder.app.Repo
import com.pillreminder.app.Scheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private enum class Status { TAKEN, UPCOMING, DUE, MISSED }

private fun statusOf(dose: Dose, data: AppData, now: LocalDateTime): Status = when {
    data.taken.containsKey(dose.key) -> Status.TAKEN
    dose.dateTime.isAfter(now) -> Status.UPCOMING
    dose.dateTime.plusHours(2).isAfter(now) -> Status.DUE
    else -> Status.MISSED
}

@Composable
fun TodayScreen(
    data: AppData,
    date: LocalDate,
    issues: List<SetupIssue>,
    onDateChange: (LocalDate) -> Unit,
    onAdd: () -> Unit,
    onMyPills: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(20_000)
        }
    }
    val today = now.toLocalDate()
    val doses = data.dosesOn(date)
    val groups = doses.groupBy { it.minute }.toSortedMap()

    Column(Modifier.fillMaxSize()) {
        AppBar("Pill Reminder", actions = {
            IconButton(onClick = onMyPills) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "My pills", tint = Color.White)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        })

        // Day selector
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day", modifier = Modifier.size(40.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onDateChange(today) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when (date) {
                        today -> "Today"
                        today.plusDays(1) -> "Tomorrow"
                        today.minusDays(1) -> "Yesterday"
                        else -> date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(Fmt.day(date), style = MaterialTheme.typography.bodyMedium, color = Muted)
                if (date != today) {
                    Text("Tap here to go back to today", style = MaterialTheme.typography.bodyMedium, color = Teal)
                }
            }
            IconButton(onClick = { onDateChange(date.plusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day", modifier = Modifier.size(40.dp))
            }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (issues.isNotEmpty()) {
                    item { SetupIssuesCard(issues) }
                }
                if (data.meds.isEmpty()) {
                    item { EmptyState(onAdd) }
                } else if (doses.isEmpty()) {
                    item {
                        Text(
                            "No pills to take on this day.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                        )
                    }
                } else {
                    val left = doses.count { !data.taken.containsKey(it.key) }
                    if (date == today) {
                        item {
                            Text(
                                if (left == 0) "All done for today. Well done!" else "$left still to take today",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (left == 0) Green else Ink,
                            )
                        }
                    }
                    groups.forEach { (minute, list) ->
                        item(key = "h$minute") {
                            Text(
                                Fmt.time(context, minute),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Teal,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(list, key = { it.key }) { dose ->
                            DoseCard(
                                dose = dose,
                                status = statusOf(dose, data, now),
                                takenAt = data.taken[dose.key],
                                canTick = !date.isAfter(today),
                                onTaken = {
                                    Repo.markTaken(context, listOf(dose.key))
                                    Scheduler.afterTickedInApp(context, dose)
                                },
                                onUndo = { Repo.unmarkTaken(context, dose.key) },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add a pill", style = MaterialTheme.typography.labelLarge) },
                containerColor = Teal,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PillPhoto(null, 120.dp, corner = 60.dp)
        Text("No pills added yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Add each pill with its photo and the times to take it. The phone will ring and show you which pill to take.",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
            textAlign = TextAlign.Center,
        )
        BigButton("Add your first pill", onAdd, Modifier.fillMaxWidth())
    }
}

@Composable
private fun DoseCard(
    dose: Dose,
    status: Status,
    takenAt: Long?,
    canTick: Boolean,
    onTaken: () -> Unit,
    onUndo: () -> Unit,
) {
    val context = LocalContext.current
    val (bg, label, labelColor) = when (status) {
        Status.TAKEN -> Triple(GreenLight, "Taken", Green)
        Status.DUE -> Triple(AmberLight, "Take now", Amber)
        Status.MISSED -> Triple(RedLight, "Missed", Red)
        Status.UPCOMING -> Triple(Color.White, "Coming up", Muted)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillPhoto(dose.med.photoPath, 96.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(dose.med.name, style = MaterialTheme.typography.titleLarge)
                    if (dose.med.instructions.isNotBlank()) {
                        Text(dose.med.instructions, style = MaterialTheme.typography.bodyLarge, color = Muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (status == Status.TAKEN && takenAt != null) {
                            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(takenAt), ZoneId.systemDefault())
                            "✓ Taken at ${Fmt.time(context, t.hour * 60 + t.minute)}"
                        } else label,
                        style = MaterialTheme.typography.titleMedium,
                        color = labelColor,
                    )
                }
            }
            if (canTick) {
                Spacer(Modifier.height(10.dp))
                if (status == Status.TAKEN) {
                    TextButton(onClick = onUndo, modifier = Modifier.align(Alignment.End)) {
                        Text("Undo", style = MaterialTheme.typography.bodyLarge, color = Muted)
                    }
                } else {
                    BigButton(
                        "✓  I've taken it",
                        onTaken,
                        Modifier.fillMaxWidth(),
                        color = if (status == Status.UPCOMING) TealLight else Green,
                        contentColor = if (status == Status.UPCOMING) Teal else Color.White,
                    )
                }
            }
        }
    }
}

