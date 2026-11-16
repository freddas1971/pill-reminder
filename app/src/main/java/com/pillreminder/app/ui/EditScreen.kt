package com.pillreminder.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pillreminder.app.AppData
import com.pillreminder.app.Fmt
import com.pillreminder.app.Frequency
import com.pillreminder.app.Medication
import com.pillreminder.app.Photos
import com.pillreminder.app.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

@Composable
fun EditScreen(data: AppData, medId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(medId) { data.meds.firstOrNull { it.id == medId } }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var instructions by remember { mutableStateOf(existing?.instructions ?: "") }
    var photoPath by remember { mutableStateOf(existing?.photoPath) }
    var times by remember { mutableStateOf(existing?.times ?: emptyList()) }
    var frequency by remember { mutableStateOf(existing?.frequency ?: Frequency.DAILY) }
    var weekdays by remember { mutableStateOf(existing?.weekdays ?: emptySet()) }
    var everyN by remember { mutableIntStateOf(existing?.everyNDays ?: 2) }
    var startDate by remember { mutableStateOf(existing?.startDate ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(existing?.endDate) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busyPhoto by remember { mutableStateOf(false) }

    fun usePicture(uri: android.net.Uri) {
        busyPhoto = true
        scope.launch {
            val path = withContext(Dispatchers.IO) { Photos.import(context, uri) }
            busyPhoto = false
            if (path != null) photoPath = path else error = "Sorry, that picture could not be used. Please try again."
        }
    }

    val cameraFile = remember { Photos.cameraTempFile(context) }
    val cameraUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) usePicture(cameraUri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) usePicture(uri)
    }

    fun addTime() {
        val now = java.time.LocalTime.now()
        TimePickerDialog(
            context,
            { _, h, m ->
                val minute = h * 60 + m
                if (minute !in times) times = (times + minute).sorted()
            },
            now.hour, 0, DateFormat.is24HourFormat(context),
        ).show()
    }

    fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, y, mo, d -> onPicked(LocalDate.of(y, mo + 1, d)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth,
        ).show()
    }

    fun save() {
        error = when {
            name.isBlank() -> "Please type the pill's name."
            times.isEmpty() -> "Please add at least one time to take it."
            frequency == Frequency.WEEKDAYS && weekdays.isEmpty() -> "Please choose at least one day."
            endDate != null && endDate!!.isBefore(startDate) -> "The last day can't be before the first day."
            else -> null
        }
        if (error != null) return
        val med = Medication(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            instructions = instructions.trim(),
            photoPath = photoPath,
            times = times.sorted(),
            frequency = frequency,
            weekdays = if (frequency == Frequency.WEEKDAYS) weekdays else emptySet(),
            everyNDays = everyN.coerceIn(2, 60),
            startDate = startDate,
            endDate = endDate,
        )
        Repo.saveMed(context, med)
        onDone()
    }

    Column(Modifier.fillMaxSize()) {
        AppBar(if (existing == null) "Add a pill" else "Change pill", navigation = {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back without saving", tint = Color.White)
            }
        })

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Photo ----
            Section("Photo of the pill") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PillPhoto(photoPath, 200.dp, corner = 32.dp)
                    if (busyPhoto) {
                        Text(
                            "Saving picture…",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Take photo", { takePhoto.launch(cameraUri) }, Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 60.dp),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(2.dp, Primary),
                    ) { Text("From gallery", style = MaterialTheme.typography.labelLarge, color = Primary) }
                }
                if (photoPath != null) {
                    TextButton(onClick = { photoPath = null }) {
                        Text("Remove photo", style = MaterialTheme.typography.bodyLarge, color = Muted)
                    }
                }
            }

            // ---- Name ----
            Section("Name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Blood pressure pill") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("How much / instructions (optional)") {
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    placeholder = { Text("e.g. 1 tablet with food") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Times ----
            Section("Times to take it") {
                if (times.isEmpty()) {
                    Text("No times yet.", style = MaterialTheme.typography.bodyLarge, color = Muted)
                }
                times.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(PrimarySoft)
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Fmt.time(context, t), style = MaterialTheme.typography.headlineSmall, color = Primary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { times = times - t }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove this time", tint = Primary)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                BigButton("+ Add a time", { addTime() }, Modifier.fillMaxWidth(), color = PrimarySoft, contentColor = PrimaryDeep)
            }

            // ---- Frequency ----
            Section("Which days") {
                FrequencyOption("Every day", frequency == Frequency.DAILY) { frequency = Frequency.DAILY }
                FrequencyOption("Only on certain days of the week", frequency == Frequency.WEEKDAYS) { frequency = Frequency.WEEKDAYS }
                if (frequency == Frequency.WEEKDAYS) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DAY_NAMES.forEachIndexed { i, label ->
                            val day = i + 1
                            val on = day in weekdays
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (on) Primary else CardWhite)
                                    .border(2.dp, Primary, RoundedCornerShape(14.dp))
                                    .clickable { weekdays = if (on) weekdays - day else weekdays + day },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label.take(2),
                                    color = if (on) Color.White else Primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                FrequencyOption("Every few days", frequency == Frequency.EVERY_N_DAYS) { frequency = Frequency.EVERY_N_DAYS }
                if (frequency == Frequency.EVERY_N_DAYS) {
                    Row(
                        Modifier.padding(start = 12.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Every", style = MaterialTheme.typography.bodyLarge)
                        StepButton("−") { everyN = (everyN - 1).coerceAtLeast(2) }
                        Text("$everyN", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
                        StepButton("+") { everyN = (everyN + 1).coerceAtMost(60) }
                        Text("days", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        "Counted from the first day below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                    )
                }
            }

            // ---- Dates ----
            Section("First day") {
                OutlinedButton(
                    onClick = { pickDate(startDate) { startDate = it } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text(Fmt.shortDate(startDate) + if (startDate == LocalDate.now()) " (today)" else "", style = MaterialTheme.typography.bodyLarge) }
            }
            Section("Last day (optional, e.g. for a short course)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { pickDate(endDate ?: startDate.plusDays(7)) { endDate = it } },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text(endDate?.let { Fmt.shortDate(it) } ?: "Keeps going (no last day)", style = MaterialTheme.typography.bodyLarge) }
                    if (endDate != null) {
                        IconButton(onClick = { endDate = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove last day")
                        }
                    }
                }
            }

            error?.let {
                Text(
                    it,
                    color = Rose,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RoseLight)
                        .padding(12.dp),
                )
            }

            BigButton("Save", { save() }, Modifier.fillMaxWidth())
            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Delete this pill", color = Rose, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("Its reminders will stop. This can't be undone.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteMed(context, existing.id)
                    onDone()
                }) { Text("Delete", color = Rose) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CardWhite)
            .border(1.5.dp, Line, RoundedCornerShape(26.dp))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

@Composable
private fun FrequencyOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PrimarySoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.headlineSmall, color = Primary) }
}
