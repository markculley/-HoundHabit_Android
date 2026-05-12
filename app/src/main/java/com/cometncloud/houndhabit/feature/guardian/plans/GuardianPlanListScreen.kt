package com.cometncloud.houndhabit.feature.guardian.plans

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.models.TrainingPlan
import com.cometncloud.houndhabit.core.services.AssignedPlan
import com.cometncloud.houndhabit.core.services.TrainingPlanService
import com.cometncloud.houndhabit.feature.guardian.pets.PetViewModel
import com.cometncloud.houndhabit.feature.trainer.plans.PlanFormScreen
import com.cometncloud.houndhabit.shared.components.PlanProgressBadge
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianPlanListScreen(
    viewModel: GuardianPlanViewModel,
    petViewModel: PetViewModel,
    onAssignedPlanClick: (AssignedPlan) -> Unit,
    onOwnPlanClick: (TrainingPlan) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val petsState by petViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TrainingPlan?>(null) }
    val planService = remember { TrainingPlanService() }
    var isCreating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
        petViewModel.loadPets()
    }
    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage
        if (msg != null) {
            snackbar.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Plans") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New plan")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.assignedPlans.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.assignedPlans.isEmpty() ->
                    EmptyState(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.assignedPlans, key = { it.id }) { ap ->
                        val isOwn = viewModel.isOwnPlan(ap)
                        PlanRow(
                            ap = ap,
                            progress = viewModel.planProgress(ap),
                            isOwn = isOwn,
                            onClick = {
                                if (isOwn) onOwnPlanClick(ap.plan)
                                else onAssignedPlanClick(ap)
                            },
                            onLongPress = { if (isOwn) pendingDelete = ap.plan },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showCreate) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCreate = false },
            sheetState = sheetState,
        ) {
            PlanFormScreen(
                editing = null,
                isSaving = isCreating,
                pets = petsState.pets,
                onCreate = { title, description, petId ->
                    scope.launch {
                        isCreating = true
                        try {
                            val plan = planService.createPlan(title, description)
                            viewModel.adoptCreatedPlan(plan, petId)
                        } catch (t: Throwable) {
                            snackbar.showSnackbar(t.message ?: "Could not create plan.")
                        } finally {
                            isCreating = false
                            sheetState.hide()
                            showCreate = false
                        }
                    }
                },
                onUpdate = { /* unused */ },
                onCancel = { scope.launch { sheetState.hide(); showCreate = false } },
            )
        }
    }

    pendingDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this plan?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOwnPlan(plan)
                    pendingDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanRow(
    ap: AssignedPlan,
    progress: com.cometncloud.houndhabit.feature.trainer.plans.PlanProgress,
    isOwn: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOwn) {
                    Text(
                        "Your plan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        " · ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ap.assignment.assignedAt?.let { ts ->
                    Text(
                        "Assigned ${formatDate(ts)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        PlanProgressBadge(progress = progress)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ListAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text("No Plans Yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(4.dp))
        Text(
            "Tap + to create your own plan, or ask your trainer to assign one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDate(instant: Instant): String {
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = java.util.TimeZone.getTimeZone(TimeZone.currentSystemDefault().id)
    }
    return df.format(Date(instant.toEpochMilliseconds()))
}
