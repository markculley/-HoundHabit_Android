package com.cometncloud.houndhabit.feature.trainer.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cometncloud.houndhabit.core.models.Distance
import com.cometncloud.houndhabit.core.models.Distraction
import com.cometncloud.houndhabit.core.models.TrainingDuration
import com.cometncloud.houndhabit.core.models.TrainingPlanItem
import com.cometncloud.houndhabit.core.models.label

data class PlanItemFormResult(
    val title: String,
    val distance: Distance,
    val duration: TrainingDuration,
    val distraction: Distraction,
    val distanceCustom: String?,
    val durationCustom: String?,
    val distractionCustom: String?,
)

@Composable
fun PlanItemFormScreen(
    editing: TrainingPlanItem?,
    onSubmit: (PlanItemFormResult) -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var distance by remember(editing?.id) { mutableStateOf(editing?.distance ?: Distance.ArmsLength) }
    var duration by remember(editing?.id) { mutableStateOf(editing?.duration ?: TrainingDuration.Instant) }
    var distraction by remember(editing?.id) { mutableStateOf(editing?.distraction ?: Distraction.None) }
    var distanceCustom by remember(editing?.id) { mutableStateOf(editing?.distanceCustom.orEmpty()) }
    var durationCustom by remember(editing?.id) { mutableStateOf(editing?.durationCustom.orEmpty()) }
    var distractionCustom by remember(editing?.id) { mutableStateOf(editing?.distractionCustom.orEmpty()) }

    val canSave = run {
        if (title.trim().isEmpty()) return@run false
        if (distance == Distance.Custom && distanceCustom.trim().isEmpty()) return@run false
        if (duration == TrainingDuration.Custom && durationCustom.trim().isEmpty()) return@run false
        if (distraction == Distraction.Custom && distractionCustom.trim().isEmpty()) return@run false
        true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (editing == null) "Add Step" else "Edit Step",
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Step name") },
            placeholder = { Text("e.g. Sit at arm's length") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "THREE D'S",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DPicker("Distance", Distance.entries.toList(), distance, { it.label }) { distance = it }
        if (distance == Distance.Custom) {
            OutlinedTextField(
                value = distanceCustom,
                onValueChange = { distanceCustom = it },
                placeholder = { Text("Enter distance…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()

        DPicker("Duration", TrainingDuration.entries.toList(), duration, { it.label }) { duration = it }
        if (duration == TrainingDuration.Custom) {
            OutlinedTextField(
                value = durationCustom,
                onValueChange = { durationCustom = it },
                placeholder = { Text("Enter duration…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()

        DPicker("Distraction", Distraction.entries.toList(), distraction, { it.label }) { distraction = it }
        if (distraction == Distraction.Custom) {
            OutlinedTextField(
                value = distractionCustom,
                onValueChange = { distractionCustom = it },
                placeholder = { Text("Enter distraction…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = {
                onSubmit(
                    PlanItemFormResult(
                        title = title.trim(),
                        distance = distance,
                        duration = duration,
                        distraction = distraction,
                        distanceCustom = if (distance == Distance.Custom) distanceCustom.trim() else null,
                        durationCustom = if (duration == TrainingDuration.Custom) durationCustom.trim() else null,
                        distractionCustom = if (distraction == Distraction.Custom) distractionCustom.trim() else null,
                    ),
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (editing == null) "Add" else "Save")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun <T> DPicker(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { idx, opt ->
                SegmentedButton(
                    selected = opt == selected,
                    onClick = { onSelect(opt) },
                    shape = SegmentedButtonDefaults.itemShape(idx, options.size),
                    label = { Text(optionLabel(opt), style = MaterialTheme.typography.bodySmall) },
                )
            }
        }
    }
}
