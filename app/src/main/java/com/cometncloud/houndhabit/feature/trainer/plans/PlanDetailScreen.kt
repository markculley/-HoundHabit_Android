package com.cometncloud.houndhabit.feature.trainer.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.cometncloud.houndhabit.core.models.Behavior
import com.cometncloud.houndhabit.core.models.PlanAssignment
import com.cometncloud.houndhabit.shared.components.PlanProgressBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    planId: String,
    viewModel: TrainerPlanViewModel,
    onBack: () -> Unit,
    onBehaviorClick: (Behavior) -> Unit,
    /** False hides the Assignments section entirely (used by own-plan view). */
    showAssignments: Boolean = true,
    /** Optional content rendered above DESCRIPTION (e.g. pet picker for own plans). */
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan = state.plans.firstOrNull { it.id == planId }
    val behaviors = state.behaviors[planId].orEmpty()
    // Read items at the outer scope so the LazyColumn re-issues rows when
    // items load (the inner lambda doesn't track viewModel.stepCount()).
    val itemsByBehavior: Map<String?, Int> = state.items[planId].orEmpty()
        .groupingBy { it.behaviorId }
        .eachCount()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showEditPlan by remember { mutableStateOf(false) }
    var showAddBehavior by remember { mutableStateOf(false) }
    var showAssignSheet by remember { mutableStateOf(false) }
    var showDeletePlanConfirm by remember { mutableStateOf(false) }
    var pendingBehaviorDelete by remember { mutableStateOf<Behavior?>(null) }
    var pendingAssignmentDelete by remember { mutableStateOf<PlanAssignment?>(null) }

    LaunchedEffect(planId) {
        viewModel.loadBehaviors(planId)
        viewModel.loadItems(planId)
        if (showAssignments) {
            viewModel.loadAssignments(planId)
            viewModel.loadLinkedGuardians()
        }
    }
    LaunchedEffect(plan == null) {
        if (plan == null && state.plans.isNotEmpty()) onBack()
    }
    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage
        if (msg != null) {
            snackbar.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    if (plan == null) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showEditPlan = true }) { Text("Edit") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (leadingContent != null) {
                item { leadingContent() }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "DESCRIPTION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val desc = plan.description
                    if (desc.isNullOrBlank()) {
                        Text(
                            "No description.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(desc, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "BEHAVIORS",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showAddBehavior = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("Add Behavior")
                    }
                }
            }

            if (behaviors.isEmpty()) {
                item {
                    Text(
                        "No behaviors yet. Tap Add Behavior to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            } else {
                itemsIndexed(behaviors, key = { _, b -> b.id }) { idx, behavior ->
                    BehaviorRow(
                        behavior = behavior,
                        stepCount = itemsByBehavior[behavior.id] ?: 0,
                        isFirst = idx == 0,
                        isLast = idx == behaviors.lastIndex,
                        onClick = { onBehaviorClick(behavior) },
                        onMoveUp = { viewModel.moveBehavior(planId, idx, idx - 1) },
                        onMoveDown = { viewModel.moveBehavior(planId, idx, idx + 1) },
                        onDelete = { pendingBehaviorDelete = behavior },
                    )
                    HorizontalDivider()
                }
            }

            if (showAssignments) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "ASSIGNMENTS",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val blockReason = assignBlockReason(behaviors, state.items[planId].orEmpty())
                        if (blockReason == null) {
                            TextButton(onClick = { showAssignSheet = true }) {
                                Text("Assign to Guardian…")
                            }
                        }
                    }
                }

                val assignments = state.assignments[planId].orEmpty()
                val blockReason = assignBlockReason(behaviors, state.items[planId].orEmpty())
                if (blockReason != null && assignments.isEmpty()) {
                    item {
                        Text(
                            blockReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else if (assignments.isEmpty()) {
                    item {
                        Text(
                            "Not assigned to anyone yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    items(assignments, key = { it.id }) { a ->
                        AssignmentRow(
                            guardianName = viewModel.guardianName(a.guardianId),
                            petName = viewModel.petName(a.petId),
                            progress = viewModel.planProgress(a),
                            onDelete = { pendingAssignmentDelete = a },
                        )
                        HorizontalDivider()
                    }
                    if (blockReason != null) {
                        item {
                            Text(
                                blockReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                TextButton(
                    onClick = { showDeletePlanConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("Delete Plan", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showEditPlan) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditPlan = false },
            sheetState = sheetState,
        ) {
            PlanFormScreen(
                editing = plan,
                isSaving = state.isLoading,
                onCreate = { _, _, _ -> /* unused in edit mode */ },
                onUpdate = { updated ->
                    viewModel.updatePlan(updated)
                    scope.launch { sheetState.hide(); showEditPlan = false }
                },
                onCancel = { scope.launch { sheetState.hide(); showEditPlan = false } },
            )
        }
    }

    if (showAddBehavior) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddBehavior = false },
            sheetState = sheetState,
        ) {
            BehaviorFormScreen(
                editing = null,
                onSubmit = { name ->
                    viewModel.addBehavior(planId, name)
                    scope.launch { sheetState.hide(); showAddBehavior = false }
                },
                onCancel = { scope.launch { sheetState.hide(); showAddBehavior = false } },
            )
        }
    }

    pendingBehaviorDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingBehaviorDelete = null },
            title = { Text("Delete this behavior?") },
            text = { Text("This will also remove all of its steps. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBehavior(target)
                    pendingBehaviorDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBehaviorDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showDeletePlanConfirm) {
        AlertDialog(
            onDismissRequest = { showDeletePlanConfirm = false },
            title = { Text("Delete this plan?") },
            text = { Text("This will also remove any behaviors, steps, and assignments. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeletePlanConfirm = false
                    viewModel.deletePlan(plan)
                    onBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlanConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showAssignSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAssignSheet = false },
            sheetState = sheetState,
        ) {
            AssignPlanSheet(
                guardians = state.linkedGuardians,
                existingAssignments = state.assignments[planId].orEmpty(),
                onAssign = { guardianId, petId ->
                    viewModel.assignPlan(planId, guardianId, petId)
                    scope.launch { sheetState.hide(); showAssignSheet = false }
                },
                onCancel = { scope.launch { sheetState.hide(); showAssignSheet = false } },
            )
        }
    }

    pendingAssignmentDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingAssignmentDelete = null },
            title = { Text("Remove this assignment?") },
            text = { Text("The guardian will lose access to this plan.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAssignment(target)
                    pendingAssignmentDelete = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAssignmentDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AssignmentRow(
    guardianName: String,
    petName: String,
    progress: PlanProgress,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(guardianName, style = MaterialTheme.typography.titleMedium)
            Text(
                "Pet: $petName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlanProgressBadge(progress = progress, modifier = Modifier.padding(end = 8.dp))
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Remove assignment",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun assignBlockReason(
    behaviors: List<Behavior>,
    items: List<com.cometncloud.houndhabit.core.models.TrainingPlanItem>,
): String? {
    if (behaviors.isEmpty()) return "Add at least one behavior before assigning."
    val empty = behaviors.filter { b -> items.none { it.behaviorId == b.id } }
    return when (empty.size) {
        0 -> null
        1 -> "\"${empty[0].name}\" has no steps. Each behavior needs at least one step."
        else -> "${empty.size} behaviors have no steps. Each behavior needs at least one step."
    }
}

@Composable
private fun BehaviorRow(
    behavior: Behavior,
    stepCount: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(behavior.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (stepCount == 1) "1 step" else "$stepCount steps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveUp, enabled = !isFirst) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = !isLast) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete behavior",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ComingSoonCard(title: String, body: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
