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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pillreminder.app.AppData
import com.pillreminder.app.Fmt
import com.pillreminder.app.Frequency
import com.pillreminder.app.Medication
import android.content.Context
import java.time.LocalDate

val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun scheduleSummary(context: Context, m: Medication): String {
    val times = m.times.joinToString(", ") { Fmt.time(context, it) }
    val how = when (m.frequency) {
        Frequency.DAILY -> "Every day"
        Frequency.WEEKDAYS -> if (m.weekdays.size == 7) "Every day" else m.weekdays.sorted().joinToString(", ") { DAY_NAMES[it - 1] }
        Frequency.EVERY_N_DAYS -> if (m.everyNDays <= 1) "Every day" else "Every ${m.everyNDays} days"
    }
    val until = m.endDate?.let { if (it.isBefore(LocalDate.now())) " (finished)" else " until ${Fmt.shortDate(it)}" } ?: ""
    val from = if (m.startDate.isAfter(LocalDate.now())) " from ${Fmt.shortDate(m.startDate)}" else ""
    return "$how at $times$from$until"
}

@Composable
fun MedsScreen(data: AppData, onBack: () -> Unit, onEdit: (String) -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        AppBar("My pills", navigation = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        })
        Box(Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (data.meds.isEmpty()) {
                    item {
                        Text("No pills yet. Tap \"Add a pill\" to start.", style = MaterialTheme.typography.bodyLarge, color = Muted)
                    }
                }
                items(data.meds, key = { it.id }) { m ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(m.id) },
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Line),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            PillPhoto(m.photoPath, 84.dp, corner = 20.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleLarge)
                                if (m.instructions.isNotBlank()) {
                                    Text(m.instructions, style = MaterialTheme.typography.bodyMedium, color = Muted)
                                }
                                Text(scheduleSummary(context, m), style = MaterialTheme.typography.bodyMedium, color = PrimaryDeep)
                            }
                            Icon(Icons.Filled.Edit, contentDescription = "Change", tint = Muted)
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
