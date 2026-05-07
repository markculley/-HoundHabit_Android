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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.cometncloud.houndhabit.core.models.TrainingPlanItem
import com.cometncloud.houndhabit.core.models.distanceLabel
import com.cometncloud.houndhabit.core.models.distractionLabel
import com.cometncloud.houndhabit.core.models.durationLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehaviorDetailScreen(
    planId: String,
    behaviorId: String,
    viewModel: TrainerPlanViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val behavior = state.behaviors[planId].orEmpty().firstOrNull { it.id == behaviorId }
    val items = remember(state.items, planId, behaviorId) {
        state.items[planId].orEmpty()
            .filter { it.behaviorId == behaviorId }
            .sortedBy { it.sortOrder }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddItem by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TrainingPlanItem?>(null) }
    var showRenameBehavior by remember { mutableStateOf(false) }
    var pendingItemDelete by remember { mutableStateOf<TrainingPlanItem?>(null) }

    LaunchedEffect(behavior == null) {
        if (behavior == null && state.behaviors[planId] != null) onBack()
    }
    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage
        if (msg != null) {
            snackbar.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    if (behavior == null) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(behavior.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showRenameBehavior = true }) { Text("Rename") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddItem = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add step")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                EmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(items, key = { _, it -> it.id }) { idx, item ->
                        ItemRow(
                            item = item,
                            index = idx,
                            isFirst = idx == 0,
                            isLast = idx == items.lastIndex,
                            onEdit = { editingItem = item },
                            onDelete = { pendingItemDelete = item },
                            onMoveUp = { viewModel.moveItem(planId, behaviorId, idx, idx - 1) },
                            onMoveDown = { viewModel.moveItem(planId, behaviorId, idx, idx + 1) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddItem) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddItem = false },
            sheetState = sheetState,
        ) {
            PlanItemFormScreen(
                editing = null,
                onSubmit = { result ->
                    viewModel.addItem(
                        planId = planId,
                        behaviorId = behaviorId,
                        title = result.title,
                        distance = result.distance,
                        duration = result.duration,
                        distraction = result.distraction,
                        distanceCustom = result.distanceCustom,
                        durationCustom = result.durationCustom,
                        distractionCustom = result.distractionCustom,
                    )
                    scope.launch { sheetState.hide(); showAddItem = false }
                },
                onCancel = { scope.launch { sheetState.hide(); showAddItem = false } },
            )
        }
    }

    val editing = editingItem
    if (editing != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { editingItem = null },
            sheetState = sheetState,
        ) {
            PlanItemFormScreen(
                editing = editing,
                onSubmit = { result ->
                    viewModel.updateItem(
                        editing.copy(
                            title = result.title,
                            distance = result.distance,
                            duration = result.duration,
                            distraction = result.distraction,
                            distanceCustom = result.distanceCustom,
                            durationCustom = result.durationCustom,
                            distractionCustom = result.distractionCustom,
                        ),
                    )
                    scope.launch { sheetState.hide(); editingItem = null }
                },
                onCancel = { scope.launch { sheetState.hide(); editingItem = null } },
            )
        }
    }

    if (showRenameBehavior) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRenameBehavior = false },
            sheetState = sheetState,
        ) {
            BehaviorFormScreen(
                editing = behavior,
                onSubmit = { newName ->
                    viewModel.updateBehavior(behavior.copy(name = newName))
                    scope.launch { sheetState.hide(); showRenameBehavior = false }
                },
                onCancel = { scope.launch { sheetState.hide(); showRenameBehavior = false } },
            )
        }
    }

    pendingItemDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingItemDelete = null },
            title = { Text("Delete this step?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(target)
                    pendingItemDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingItemDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ItemRow(
    item: TrainingPlanItem,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThreeDTag(item.distanceLabel)
                ThreeDTag(item.durationLabel)
                ThreeDTag(item.distractionLabel)
            }
        }
        IconButton(onClick = onMoveUp, enabled = !isFirst) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = !isLast) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit step")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete step",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ThreeDTag(label: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = CircleShape,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No steps yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(4.dp))
        Text(
            "Tap + to add the first step.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
