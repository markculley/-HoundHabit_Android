package com.cometncloud.houndhabit.feature.trainer.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cometncloud.houndhabit.core.models.LinkedGuardian
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.models.PlanAssignment
import com.cometncloud.houndhabit.core.services.PetService

/**
 * Sheet UI for assigning a plan to a linked guardian + optional pet.
 *
 * Hosts its own pet-fetch on guardian selection (independent of the trainer
 * plan VM, since the pet cache there is keyed by id and not bucketed by
 * guardian — easier to fetch fresh here).
 */
@Composable
fun AssignPlanSheet(
    guardians: List<LinkedGuardian>,
    existingAssignments: List<PlanAssignment>,
    onAssign: (guardianId: String, petId: String?) -> Unit,
    onCancel: () -> Unit,
) {
    val available = remember(guardians, existingAssignments) {
        val taken = existingAssignments.map { it.guardianId }.toSet()
        guardians.filterNot { it.guardianId in taken }
    }
    var selectedGuardian by remember { mutableStateOf<LinkedGuardian?>(null) }
    var pets by remember { mutableStateOf<List<Pet>>(emptyList()) }
    var selectedPet by remember { mutableStateOf<Pet?>(null) }
    var guardianMenuOpen by remember { mutableStateOf(false) }
    var petMenuOpen by remember { mutableStateOf(false) }
    val petService = remember { PetService() }

    LaunchedEffect(selectedGuardian?.guardianId) {
        selectedPet = null
        val gid = selectedGuardian?.guardianId
        pets = if (gid == null) emptyList()
        else runCatching { petService.fetchPets(gid) }.getOrDefault(emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Assign Plan", style = MaterialTheme.typography.headlineSmall)

        Text(
            "GUARDIAN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (available.isEmpty()) {
            Text(
                if (guardians.isEmpty())
                    "You have no linked guardians. Send an invite first."
                else
                    "All linked guardians are already assigned this plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Box {
                OutlinedButton(
                    onClick = { guardianMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        selectedGuardian?.profile?.fullName ?: "Select a guardian",
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾")
                }
                DropdownMenu(
                    expanded = guardianMenuOpen,
                    onDismissRequest = { guardianMenuOpen = false },
                ) {
                    available.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g.profile.fullName ?: "Guardian") },
                            onClick = {
                                selectedGuardian = g
                                guardianMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        if (selectedGuardian != null) {
            Text(
                "PET (OPTIONAL)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                OutlinedButton(
                    onClick = { petMenuOpen = true },
                    enabled = pets.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        selectedPet?.name ?: if (pets.isEmpty()) "No pets to choose" else "Any pet",
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾")
                }
                DropdownMenu(
                    expanded = petMenuOpen,
                    onDismissRequest = { petMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Any pet") },
                        onClick = {
                            selectedPet = null
                            petMenuOpen = false
                        },
                    )
                    pets.forEach { pet ->
                        DropdownMenuItem(
                            text = { Text(pet.name) },
                            onClick = {
                                selectedPet = pet
                                petMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    val g = selectedGuardian ?: return@Button
                    onAssign(g.guardianId, selectedPet?.id)
                },
                enabled = selectedGuardian != null,
                modifier = Modifier.weight(1f),
            ) { Text("Assign") }
        }
    }
}
