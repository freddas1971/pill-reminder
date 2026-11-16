package com.pillreminder.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pillreminder.app.AppData
import com.pillreminder.app.Dose
import com.pillreminder.app.Fmt
import com.pillreminder.app.Repo
import com.pillreminder.app.Scheduler
import kotlinx.coroutines.delay
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

/** Friendly label and colours for the part of the day. */
private data class DayPart(val label: String, val bg: Color, val fg: Color)

private fun dayPart(minute: Int): DayPart = when (minute / 60) {
    in 4..11 -> DayPart("☀  Morning", Color(0xFFFBE7C4), Color(0xFF8A5A0F))
    in 12..16 -> DayPart("◐  Afternoon", PrimarySoft, PrimaryDeep)
    in 17..20 -> DayPart("☾  Evening", Color(0xFFE6E2F0), Color(0xFF4E4673))
    else -> DayPart("✦  Night", Color(0xFFDDE3EA), Color(0xFF34465A))
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
    val done = doses.count { data.taken.containsKey(it.key) }

    Column(Modifier.fillMaxSize()) {
        // ---- Header with the day ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(HeaderShape)
                .background(Brush.verticalGradient(listOf(Primary, PrimaryDeep)))
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pill Reminder",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                HeaderIcon(Icons.AutoMirrored.Filled.List, "My pills", onMyPills)
                HeaderIcon(Icons.Filled.Settings, "Settings", onSettings)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day",
                        tint = Color.White, modifier = Modifier.size(40.dp),
                    )
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
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                    )
                    Text(Fmt.day(date), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
                    if (date != today) {
                        Spacer(Modifier.height(6.dp))
                        Chip("Tap to go back to today", Color.White.copy(alpha = 0.22f), Color.White)
                    }
                }
                IconButton(onClick = { onDateChange(date.plusDays(1)) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day",
                        tint = Color.White, modifier = Modifier.size(40.dp),
                    )
                }
            }
            if (doses.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        if (done == doses.size) "All $done done. Well done!" else "$done of ${doses.size} taken",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / doses.size },
                        color = Color(0xFFCFE3C6),
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (issues.isNotEmpty()) {
                    item { SetupIssuesCard(issues) }
                }
                if (data.meds.isEmpty()) {
                    item { EmptyState(onAdd) }
                } else if (doses.isEmpty()) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PillPhoto(null, 90.dp, corner = 45.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No pills to take on this day.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Muted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    groups.forEach { (minute, list) ->
                        item(key = "h$minute") {
                            val part = dayPart(minute)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            ) {
                                Text(Fmt.time(context, minute), style = MaterialTheme.typography.headlineMedium, color = Ink)
                                Spacer(Modifier.width(12.dp))
                                Chip(part.label, part.bg, part.fg)
                            }
                        }
                        items(list, key = { it.key }) { dose ->
                            DoseCard(
                                dose = dose,
                                status = statusOf(dose, data, now),
                                takenAt = data.taken[dose.key],
                                canTick = !date.isAfter(today),
                                onTaken = { Scheduler.tickInApp(context, dose) },
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
                containerColor = Primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
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
}

private data class CardLook(val bg: Color, val border: Color, val chipBg: Color, val chipFg: Color, val label: String)

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
    val look = when (status) {
        Status.TAKEN -> CardLook(SageLight, SageLight, Sage, Color.White, "✓ Taken")
        Status.DUE -> CardLook(HoneyLight, Color(0xFFEBC98A), Honey, Color.White, "Take now")
        Status.MISSED -> CardLook(RoseLight, Color(0xFFE7B3A8), Rose, Color.White, "Missed")
        Status.UPCOMING -> CardLook(CardWhite, Line, Color(0xFFF1E6D8), Muted, "Coming up")
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = look.bg),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.5.dp, look.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillPhoto(dose.med.photoPath, 100.dp, corner = 22.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Chip(
                        if (status == Status.TAKEN && takenAt != null) {
                            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(takenAt), ZoneId.systemDefault())
                            "✓ Taken ${Fmt.time(context, t.hour * 60 + t.minute)}"
                        } else look.label,
                        look.chipBg,
                        look.chipFg,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(dose.med.name, style = MaterialTheme.typography.titleLarge, color = Ink)
                    if (dose.med.instructions.isNotBlank()) {
                        Text(dose.med.instructions, style = MaterialTheme.typography.bodyLarge, color = Muted)
                    }
                }
            }
            if (canTick) {
                Spacer(Modifier.height(12.dp))
                if (status == Status.TAKEN) {
                    TextButton(onClick = onUndo, modifier = Modifier.align(Alignment.End)) {
                        Text("Undo", style = MaterialTheme.typography.bodyLarge, color = Muted)
                    }
                } else {
                    BigButton(
                        "✓  I've taken it",
                        onTaken,
                        Modifier.fillMaxWidth(),
                        color = if (status == Status.UPCOMING) PrimarySoft else Sage,
                        contentColor = if (status == Status.UPCOMING) PrimaryDeep else Color.White,
                    )
                }
            }
        }
    }
}
