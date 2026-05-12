package com.cometncloud.houndhabit.feature.guardian.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cometncloud.houndhabit.core.models.Distance
import com.cometncloud.houndhabit.core.models.Distraction
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.models.TrainingDuration
import com.cometncloud.houndhabit.core.models.TrainingRecord
import com.cometncloud.houndhabit.shared.components.TrainingTimerSection
import com.cometncloud.houndhabit.core.models.TrainingStatus
import com.cometncloud.houndhabit.core.models.label
import com.cometncloud.houndhabit.shared.components.StatusBadge
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.text.DateFormat
import java.util.Date

/**
 * Body of the log/edit-session sheet. Hosted inside a `ModalBottomSheet`.
 *
 * Standalone (non-plan) sessions only — plan-linked sessions land in Phase 9.
 *
 * @param pets the current guardian's pets, for the picker.
 * @param preselectedPetId pet to default the picker to when [editing] is null.
 * @param editing existing record to edit, or null to create.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrainingRecordFormScreen(
    pets: List<Pet>,
    preselectedPetId: String?,
    editing: TrainingRecord?,
    isSaving: Boolean,
    onCreate: (
        petId: String,
        recordedAt: kotlinx.datetime.Instant,
        score: Int,
        distance: Distance,
        distraction: Distraction,
        duration: TrainingDuration,
        notes: String?,
        isShared: Boolean,
    ) -> Unit,
    onUpdate: (TrainingRecord) -> Unit,
    onCancel: () -> Unit,
) {
    val tz = remember { TimeZone.currentSystemDefault() }

    var selectedPetId by remember { mutableStateOf(editing?.petId ?: preselectedPetId ?: pets.firstOrNull()?.id) }
    var recordedAt by remember {
        mutableStateOf(editing?.recordedAt ?: noonLocalToday(tz))
    }
    var score by remember { mutableStateOf(editing?.score ?: 5) }
    var distance by remember { mutableStateOf(editing?.distance ?: Distance.ArmsLength) }
    var distraction by remember { mutableStateOf(editing?.distraction ?: Distraction.None) }
    var duration by remember { mutableStateOf(editing?.duration ?: TrainingDuration.Instant) }
    var notes by remember { mutableStateOf(editing?.notes.orEmpty()) }
    var isShared by remember { mutableStateOf(editing?.isShared ?: false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var petPickerExpanded by remember { mutableStateOf(false) }

    val derivedStatus = TrainingStatus.fromScore(score)
    val canSave = selectedPetId != null && !isSaving

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (editing == null) "Log Session" else "Edit Session",
            style = MaterialTheme.typography.headlineSmall,
        )

        // Pet picker
        SectionLabel("Pet")
        if (pets.isEmpty()) {
            Text(
                "No pets yet — add a pet first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val petName = pets.firstOrNull { it.id == selectedPetId }?.name ?: "Select a pet"
            androidx.compose.foundation.layout.Box {
                OutlinedButton(
                    onClick = { petPickerExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(petName, modifier = Modifier.weight(1f))
                    Text("▾")
                }
                DropdownMenu(
                    expanded = petPickerExpanded,
                    onDismissRequest = { petPickerExpanded = false },
                ) {
                    pets.forEach { pet ->
                        DropdownMenuItem(
                            text = { Text(pet.name) },
                            onClick = {
                                selectedPetId = pet.id
                                petPickerExpanded = false
                            },
                        )
                    }
                }
            }
        }

        // Date
        SectionLabel("Date")
        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(formatDate(recordedAt, tz))
        }

        // Score
        SectionLabel("Reps out of 5")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = { if (score > 0) score-- },
                enabled = score > 0,
            ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease score") }
            Text(
                "$score / 5",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(
                onClick = { if (score < 5) score++ },
                enabled = score < 5,
            ) { Icon(Icons.Filled.Add, contentDescription = "Increase score") }
            Spacer(Modifier.weight(1f))
            StatusBadge(status = derivedStatus, size = 16.dp)
            Spacer(Modifier.size(6.dp))
            Text(
                derivedStatus.shortLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            scoreFooter(score),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Three D's
        SectionLabel("Three D's")
        ChipPicker(
            label = "Distance",
            options = Distance.entries,
            selected = distance,
            optionLabel = { it.label },
            onSelect = { distance = it },
        )
        ChipPicker(
            label = "Distraction",
            options = Distraction.entries,
            selected = distraction,
            optionLabel = { it.label },
            onSelect = { distraction = it },
        )
        ChipPicker(
            label = "Duration",
            options = TrainingDuration.entries,
            selected = duration,
            optionLabel = { it.label },
            onSelect = { duration = it },
        )

        // Notes
        SectionLabel("Notes")
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = { Text("Optional notes…") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
        )

        TrainingTimerSection()

        // Share toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Share with Trainer", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = isShared, onCheckedChange = { isShared = it })
        }

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                enabled = !isSaving,
            ) { Text("Cancel") }

            Button(
                onClick = {
                    val petId = selectedPetId ?: return@Button
                    val trimmedNotes = notes.trim().ifEmpty { null }
                    if (editing != null) {
                        onUpdate(
                            editing.copy(
                                petId = petId,
                                recordedAt = recordedAt,
                                score = score,
                                status = TrainingStatus.fromScore(score),
                                distance = distance,
                                distraction = distraction,
                                duration = duration,
                                notes = trimmedNotes,
                                isShared = isShared,
                            ),
                        )
                    } else {
                        onCreate(
                            petId, recordedAt, score, distance, distraction, duration,
                            trimmedNotes, isShared,
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = canSave,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (editing == null) "Log" else "Save")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = recordedAt.toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        // M3 DatePicker returns UTC midnight of the picked date.
                        // Renormalize to noon-local so timezone shifts can't move the calendar date.
                        val utcDate = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
                            .toLocalDateTime(TimeZone.UTC).date
                        recordedAt = LocalDateTime(utcDate, LocalTime(12, 0)).toInstant(tz)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipPicker(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

private val TrainingStatus.shortLabel: String
    get() = when (this) {
        TrainingStatus.Red -> "Red"
        TrainingStatus.Orange -> "Orange"
        TrainingStatus.Yellow -> "Yellow"
        TrainingStatus.Green -> "Green"
    }

private fun scoreFooter(score: Int): String = when (score) {
    5 -> "Green — ready to advance to the next step."
    3, 4 -> "Yellow — keep practicing this step to build confidence."
    2 -> "Orange — stay on this step a bit longer."
    else -> "Red — we'll drop back to the previous step."
}

private fun noonLocalToday(tz: TimeZone): kotlinx.datetime.Instant {
    val today = Clock.System.now().toLocalDateTime(tz).date
    return LocalDateTime(today, LocalTime(12, 0)).toInstant(tz)
}

private fun formatDate(instant: kotlinx.datetime.Instant, tz: TimeZone): String {
    val df = DateFormat.getDateInstance(DateFormat.LONG).apply {
        timeZone = java.util.TimeZone.getTimeZone(tz.id)
    }
    return df.format(Date(instant.toEpochMilliseconds()))
}
