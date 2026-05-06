package com.cometncloud.houndhabit.feature.trainer.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.cometncloud.houndhabit.core.models.TrainingPlan

/**
 * Create/edit form for a [TrainingPlan]. Hosted inside a [ModalBottomSheet]
 * by the caller. The pet picker that appears for guardian self-create
 * arrives in Phase 9d.
 */
@Composable
fun PlanFormScreen(
    editing: TrainingPlan?,
    isSaving: Boolean,
    onCreate: (title: String, description: String?) -> Unit,
    onUpdate: (TrainingPlan) -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var description by remember(editing?.id) { mutableStateOf(editing?.description.orEmpty()) }

    val canSave = title.trim().isNotEmpty() && !isSaving

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (editing == null) "New Plan" else "Edit Plan",
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Plan title") },
            placeholder = { Text("e.g. First Steps") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (optional)") },
            placeholder = { Text("Optional overview of the plan…") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                val trimmedTitle = title.trim()
                val trimmedDesc = description.trim().ifBlank { null }
                if (editing == null) {
                    onCreate(trimmedTitle, trimmedDesc)
                } else {
                    onUpdate(editing.copy(title = trimmedTitle, description = trimmedDesc))
                }
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(if (editing == null) "Create" else "Save")
            }
        }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
