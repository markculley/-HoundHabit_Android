package com.cometncloud.houndhabit.feature.guardian.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.TrainingPlanItem
import com.cometncloud.houndhabit.core.models.TrainingStatus
import com.cometncloud.houndhabit.core.models.distanceLabel
import com.cometncloud.houndhabit.core.models.distractionLabel
import com.cometncloud.houndhabit.core.models.durationLabel
import com.cometncloud.houndhabit.core.services.PetService
import com.cometncloud.houndhabit.core.services.TrainingRecordService
import com.cometncloud.houndhabit.shared.components.StatusBadge
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Plan-linked log sheet. Three D's locked from the step, no per-session
 * sharing toggle (uses assignment's `is_shared`), no pet picker.
 *
 * Lives next to the standalone [TrainingRecordFormScreen] but uses a
 * dedicated path because too much branching would make the standalone form
 * unwieldy. On Log, fires [onLogged] with the 0–5 score so the caller
 * (plan VM) can run advancement logic.
 */
@Composable
fun PlanLinkedRecordSheet(
    planItem: TrainingPlanItem,
    behaviorName: String?,
    lockedPetId: String?,
    isShared: Boolean,
    onLogged: (score: Int) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val recordService = remember { TrainingRecordService() }
    val petService = remember { PetService() }

    var score by remember { mutableStateOf(5) }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var petName by remember { mutableStateOf<String?>(null) }

    val tz = remember { TimeZone.currentSystemDefault() }
    val derived = TrainingStatus.fromScore(score)

    LaunchedEffect(lockedPetId) {
        val gid = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@LaunchedEffect
        val pets = runCatching { petService.fetchPets(gid) }.getOrDefault(emptyList())
        petName = if (lockedPetId != null) pets.firstOrNull { it.id == lockedPetId }?.name
        else pets.firstOrNull()?.name
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Step ${planItem.sortOrder + 1}: ${planItem.title}",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (behaviorName != null) {
            LabeledRow("Behavior", behaviorName)
        }
        LabeledRow("Pet", petName ?: "—")

        HorizontalDivider()

        Text(
            "THREE D'S",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabeledRow("Distance", planItem.distanceLabel)
        LabeledRow("Duration", planItem.durationLabel)
        LabeledRow("Distraction", planItem.distractionLabel)

        HorizontalDivider()

        Text(
            "REPS OUT OF 5",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (score > 0) score-- },
                enabled = score > 0,
            ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }
            Text(
                "$score / 5",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(
                onClick = { if (score < 5) score++ },
                enabled = score < 5,
            ) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
            Spacer(Modifier.weight(1f))
            StatusBadge(status = derived, size = 16.dp)
            Spacer(Modifier.size(6.dp))
            Text(
                derived.shortLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            scoreFooter(score),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )

        errorMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

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
                    val pid = lockedPetId
                    if (pid == null) {
                        errorMessage = "No pet linked to this assignment yet."
                        return@Button
                    }
                    val gid = SupabaseClient.client.auth.currentUserOrNull()?.id
                    if (gid == null) {
                        errorMessage = "Not signed in."
                        return@Button
                    }
                    val recordedAt = nowAtNoon(tz)
                    isSaving = true
                    errorMessage = null
                    scope.launch {
                        try {
                            recordService.createRecord(
                                petId = pid,
                                guardianId = gid,
                                recordedAt = recordedAt,
                                score = score,
                                distance = planItem.distance,
                                distraction = planItem.distraction,
                                duration = planItem.duration,
                                distanceCustom = planItem.distanceCustom,
                                durationCustom = planItem.durationCustom,
                                distractionCustom = planItem.distractionCustom,
                                notes = notes.trim().ifBlank { null },
                                isShared = isShared,
                                planItemId = planItem.id,
                            )
                            onLogged(score)
                        } catch (t: Throwable) {
                            errorMessage = t.message ?: "Could not log session."
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Log")
                }
            }
        }

        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun nowAtNoon(tz: TimeZone): kotlinx.datetime.Instant {
    val today = Clock.System.now().toLocalDateTime(tz).date
    return LocalDateTime(today, LocalTime(12, 0)).toInstant(tz)
}
