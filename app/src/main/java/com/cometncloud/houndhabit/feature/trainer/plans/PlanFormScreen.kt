package com.cometncloud.houndhabit.feature.trainer.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.models.TrainingPlan

/**
 * Create/edit form for a [TrainingPlan]. Hosted inside a [ModalBottomSheet].
 *
 * Trainer call sites pass `pets = emptyList()` (no picker, second arg of
 * onCreate ignored). Guardian self-create call sites pass their pets so
 * the user can link the new plan to one at creation time. iOS parity:
 * picker only shows in create mode and only when pets are non-empty.
 */
@Composable
fun PlanFormScreen(
    editing: TrainingPlan?,
    isSaving: Boolean,
    pets: List<Pet> = emptyList(),
    /** Pre-selects a pet (used when entering this form from a pet's detail page). */
    fixedPetId: String? = null,
    onCreate: (title: String, description: String?, petId: String?) -> Unit,
    onUpdate: (TrainingPlan) -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var description by remember(editing?.id) { mutableStateOf(editing?.description.orEmpty()) }
    var selectedPetId by remember(editing?.id) { mutableStateOf(fixedPetId) }
    var petMenuOpen by remember { mutableStateOf(false) }

    val showPetPicker = editing == null && pets.isNotEmpty() && fixedPetId == null
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

        if (showPetPicker) {
            Text(
                "ASSIGN TO PET",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                OutlinedButton(
                    onClick = { petMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val current = pets.firstOrNull { it.id == selectedPetId }?.name ?: "None"
                    Text(current, modifier = Modifier.weight(1f))
                    Text("▾")
                }
                DropdownMenu(
                    expanded = petMenuOpen,
                    onDismissRequest = { petMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = { selectedPetId = null; petMenuOpen = false },
                    )
                    pets.forEach { pet ->
                        DropdownMenuItem(
                            text = { Text(pet.name) },
                            onClick = { selectedPetId = pet.id; petMenuOpen = false },
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val trimmedTitle = title.trim()
                val trimmedDesc = description.trim().ifBlank { null }
                if (editing == null) {
                    onCreate(trimmedTitle, trimmedDesc, fixedPetId ?: selectedPetId)
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
