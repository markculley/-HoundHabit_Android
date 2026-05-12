package com.cometncloud.houndhabit.feature.guardian.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cometncloud.houndhabit.core.models.Behavior
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.feature.trainer.plans.PlanDetailScreen
import com.cometncloud.houndhabit.feature.trainer.plans.TrainerPlanViewModel

/**
 * Hosts the trainer's [PlanDetailScreen] in own-plan mode (no Assignments
 * section). The guardian VM owns the plan list — but the trainer VM owns
 * behaviors + items. We seed the trainer VM's `plans` field from the
 * guardian's known plan so the screen can resolve title/description.
 *
 * The pet-picker row (top of the screen) reads + writes through the
 * guardian VM so its optimistic state stays in sync with PetDetail.
 *
 * Lives in the guardian package because routing is from the guardian's
 * Plans tab; the wrapper just composes the existing trainer screen.
 */
@Composable
fun OwnedPlanDetailScreen(
    planId: String,
    planViewModel: GuardianPlanViewModel,
    pets: List<Pet>,
    onBack: () -> Unit,
    onBehaviorClick: (Behavior) -> Unit,
    viewModel: TrainerPlanViewModel = viewModel(),
) {
    val planState by planViewModel.state.collectAsStateWithLifecycle()
    val ap = planState.assignedPlans.firstOrNull { it.plan.id == planId }
    val trainerState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(planId, ap?.plan?.id) {
        // Seed the VM's plans list with this plan so PlanDetailScreen finds it
        // by id without a separate fetchPlans call (which would fail anyway —
        // this plan is owned by the guardian, not the trainer).
        val plan = ap?.plan ?: return@LaunchedEffect
        if (trainerState.plans.none { it.id == plan.id }) {
            viewModel.adoptPlan(plan)
        }
    }

    // The assignment may briefly be null between navigation and the next state
    // emission (e.g. just-deleted). Pop in that case instead of rendering a
    // broken screen.
    LaunchedEffect(ap == null, planState.isLoading) {
        if (ap == null && !planState.isLoading) onBack()
    }
    if (ap == null) return

    PlanDetailScreen(
        planId = ap.plan.id,
        viewModel = viewModel,
        onBack = onBack,
        onBehaviorClick = onBehaviorClick,
        showAssignments = false,
        leadingContent = {
            PetPickerRow(
                pets = pets,
                currentPetId = ap.assignment.petId,
                onSelect = { petId -> planViewModel.updateAssignmentPet(ap, petId) },
            )
            HorizontalDivider()
        },
    )
}

@Composable
private fun PetPickerRow(
    pets: List<Pet>,
    currentPetId: String?,
    onSelect: (String?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val currentName = pets.firstOrNull { it.id == currentPetId }?.name ?: "None"

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "ASSIGNED TO PET",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(
                onClick = { menuOpen = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = pets.isNotEmpty(),
            ) {
                Text(
                    if (pets.isEmpty()) "No pets yet" else currentName,
                    modifier = Modifier.weight(1f),
                )
                Text("▾")
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        menuOpen = false
                        if (currentPetId != null) onSelect(null)
                    },
                )
                pets.forEach { pet ->
                    DropdownMenuItem(
                        text = { Text(pet.name) },
                        onClick = {
                            menuOpen = false
                            if (pet.id != currentPetId) onSelect(pet.id)
                        },
                    )
                }
            }
        }
    }
}
