package com.cometncloud.houndhabit.feature.guardian.pets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cometncloud.houndhabit.core.models.TrainingPlan
import com.cometncloud.houndhabit.core.services.AssignedPlan
import com.cometncloud.houndhabit.core.services.TrainingPlanService
import com.cometncloud.houndhabit.feature.guardian.plans.AssignExistingPlanSheet
import com.cometncloud.houndhabit.feature.guardian.plans.GuardianPlanViewModel
import com.cometncloud.houndhabit.feature.trainer.plans.PlanFormScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetDetailScreen(
    petId: String,
    viewModel: PetViewModel = viewModel(),
    planViewModel: GuardianPlanViewModel,
    onBack: () -> Unit,
    onTrainingSessions: (() -> Unit)? = null,
    onAssignedPlanClick: (AssignedPlan) -> Unit,
    onOwnPlanClick: (TrainingPlan) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val planState by planViewModel.state.collectAsStateWithLifecycle()
    val pet = state.pets.firstOrNull { it.id == petId }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val planService = remember { TrainingPlanService() }

    var menuOpen by remember { mutableStateOf(false) }
    var showNewPlan by remember { mutableStateOf(false) }
    var showAssignExisting by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { planViewModel.load() }

    val petPlans = planState.assignedPlans.filter { it.assignment.petId == petId }
    val myPetPlans = petPlans.filter { planViewModel.isOwnPlan(it) }
    val trainerPetPlans = petPlans.filter { !planViewModel.isOwnPlan(it) }
    // Sheet candidates: any of the guardian's own plans not currently linked
    // here, plus trainer-assigned plans the trainer left as "Any pet". The
    // guardian decides which pet to apply those to.
    val assignableHere = planState.assignedPlans.filter { ap ->
        if (planViewModel.isOwnPlan(ap)) ap.assignment.petId != petId
        else ap.assignment.petId == null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pet?.name ?: "Pet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (pet == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    "Pet not found.",
                    modifier = Modifier.padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Hero
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val cacheBusted = pet.photoUrl?.let { "$it?t=${pet.updatedAt.epochSeconds}" }
                    PetAvatar(model = cacheBusted, size = 160.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(pet.name, style = MaterialTheme.typography.headlineSmall)
                    val breed = pet.breed
                    if (!breed.isNullOrBlank()) {
                        Text(
                            breed,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (onTrainingSessions != null) {
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = onTrainingSessions,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Training Sessions")
                        }
                    }
                }

                HorizontalDivider()

                // Plans header + "+" menu
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "PLANS",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add plan")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Plan") },
                                onClick = { menuOpen = false; showNewPlan = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Assign Existing Plan") },
                                enabled = assignableHere.isNotEmpty(),
                                onClick = { menuOpen = false; showAssignExisting = true },
                            )
                        }
                    }
                }

                if (myPetPlans.isNotEmpty()) {
                    SubHeader("My Plans")
                    myPetPlans.forEach { ap ->
                        PlanRow(ap = ap, onClick = { onOwnPlanClick(ap.plan) })
                        HorizontalDivider()
                    }
                }
                if (trainerPetPlans.isNotEmpty()) {
                    SubHeader("From My Trainer")
                    trainerPetPlans.forEach { ap ->
                        PlanRow(ap = ap, onClick = { onAssignedPlanClick(ap) })
                        HorizontalDivider()
                    }
                }
                if (myPetPlans.isEmpty() && trainerPetPlans.isEmpty()) {
                    Text(
                        "No plans linked to ${pet.name} yet.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showNewPlan && pet != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNewPlan = false },
            sheetState = sheetState,
        ) {
            PlanFormScreen(
                editing = null,
                isSaving = isCreating,
                fixedPetId = pet.id,
                onCreate = { title, description, fixedPetId ->
                    scope.launch {
                        isCreating = true
                        try {
                            val plan = planService.createPlan(title, description)
                            planViewModel.adoptCreatedPlan(plan, fixedPetId ?: pet.id)
                        } catch (t: Throwable) {
                            snackbar.showSnackbar(t.message ?: "Could not create plan.")
                        } finally {
                            isCreating = false
                            sheetState.hide()
                            showNewPlan = false
                        }
                    }
                },
                onUpdate = { /* unused */ },
                onCancel = { scope.launch { sheetState.hide(); showNewPlan = false } },
            )
        }
    }

    if (showAssignExisting && pet != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAssignExisting = false },
            sheetState = sheetState,
        ) {
            AssignExistingPlanSheet(
                candidates = assignableHere,
                isOwnPlan = { planViewModel.isOwnPlan(it) },
                onPick = { ap ->
                    planViewModel.updateAssignmentPet(ap, pet.id)
                    scope.launch { sheetState.hide(); showAssignExisting = false }
                },
                onCancel = { scope.launch { sheetState.hide(); showAssignExisting = false } },
            )
        }
    }
}

@Composable
private fun SubHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanRow(ap: AssignedPlan, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(ap.plan.title, style = MaterialTheme.typography.titleMedium)
            val desc = ap.plan.description
            if (!desc.isNullOrBlank()) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    CircleShape,
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                "Open",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
