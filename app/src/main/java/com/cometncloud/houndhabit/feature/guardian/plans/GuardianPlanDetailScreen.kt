package com.cometncloud.houndhabit.feature.guardian.plans

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cometncloud.houndhabit.core.models.TrainingPlanItem
import com.cometncloud.houndhabit.core.models.distanceLabel
import com.cometncloud.houndhabit.core.models.distractionLabel
import com.cometncloud.houndhabit.core.models.durationLabel
import com.cometncloud.houndhabit.feature.guardian.records.PlanLinkedRecordSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianPlanDetailScreen(
    assignedPlanId: String,
    viewModel: GuardianPlanViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ap = state.assignedPlans.firstOrNull { it.id == assignedPlanId }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var practiceItem by remember { mutableStateOf<TrainingPlanItem?>(null) }
    var infoItem by remember { mutableStateOf<TrainingPlanItem?>(null) }

    LaunchedEffect(ap?.plan?.id) {
        if (ap != null) viewModel.loadPlanDetails(ap.plan.id)
    }
    LaunchedEffect(ap == null) {
        if (ap == null && state.assignedPlans.isNotEmpty()) onBack()
    }
    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage
        if (msg != null) {
            snackbar.showSnackbar(msg)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.lastAdvancementMessage) {
        val msg = state.lastAdvancementMessage
        if (msg != null) {
            snackbar.showSnackbar(msg)
            viewModel.clearAdvancementMessage()
        }
    }

    if (ap == null) {
        // Either still loading on first launch, or the assignment was just
        // deleted; the LaunchedEffect above already kicks us back in the
        // latter case. Show a spinner in the meantime instead of a blank
        // surface flash.
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }
        return
    }

    val planId = ap.plan.id
    val ordered = viewModel.orderedItems(planId)
    val currentItem = viewModel.currentItem(ap, ordered)
    val currentIdx = ordered.indexOfFirst { it.id == currentItem?.id }.let { if (it < 0) 0 else it }
    val behaviors = state.behaviors[planId].orEmpty().sortedBy { it.sortOrder }
    val itemsByBehavior = state.items[planId].orEmpty().groupBy { it.behaviorId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ap.plan.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val desc = ap.plan.description
                    if (!desc.isNullOrBlank()) {
                        Text(desc, style = MaterialTheme.typography.bodyMedium)
                    }
                    val isTrainerAssigned = ap.assignment.trainerId != ap.assignment.guardianId
                    if (isTrainerAssigned) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Share sessions with trainer",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Switch(
                                checked = ap.assignment.isShared,
                                onCheckedChange = { viewModel.updateSharing(ap, it) },
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            if (ordered.isEmpty()) {
                item {
                    Text(
                        "No steps have been added to this plan yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else if (behaviors.isEmpty()) {
                // Legacy / pre-behavior plans — flat list under "Steps".
                item { SectionHeader("Steps") }
                items(ordered, key = { it.id }) { item ->
                    val itemIdx = ordered.indexOfFirst { it.id == item.id }
                    StepRow(
                        item = item,
                        isCurrent = item.id == currentItem?.id,
                        isLocked = itemIdx > currentIdx,
                        onTap = {
                            if (itemIdx > currentIdx) infoItem = item else practiceItem = item
                        },
                    )
                    HorizontalDivider()
                }
            } else {
                behaviors.forEach { b ->
                    val behaviorItems = itemsByBehavior[b.id].orEmpty().sortedBy { it.sortOrder }
                    if (behaviorItems.isNotEmpty()) {
                        item { SectionHeader(b.name) }
                        items(behaviorItems, key = { it.id }) { item ->
                            val itemIdx = ordered.indexOfFirst { it.id == item.id }
                            StepRow(
                                item = item,
                                isCurrent = item.id == currentItem?.id,
                                isLocked = itemIdx > currentIdx,
                                onTap = {
                                    if (itemIdx > currentIdx) infoItem = item else practiceItem = item
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                val unbound = itemsByBehavior[null].orEmpty().sortedBy { it.sortOrder }
                if (unbound.isNotEmpty()) {
                    item { SectionHeader("Other Steps") }
                    items(unbound, key = { it.id }) { item ->
                        val itemIdx = ordered.indexOfFirst { it.id == item.id }
                        StepRow(
                            item = item,
                            isCurrent = item.id == currentItem?.id,
                            isLocked = itemIdx > currentIdx,
                            onTap = {
                                if (itemIdx > currentIdx) infoItem = item else practiceItem = item
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    practiceItem?.let { item ->
        val behaviorName = behaviors.firstOrNull { it.id == item.behaviorId }?.name
        val isCurrentStep = item.id == currentItem?.id
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { practiceItem = null },
            sheetState = sheetState,
        ) {
            PlanLinkedRecordSheet(
                planItem = item,
                behaviorName = behaviorName,
                lockedPetId = ap.assignment.petId,
                isShared = ap.assignment.isShared,
                onLogged = { score ->
                    if (isCurrentStep) viewModel.advanceCurrentStep(ap, score)
                    scope.launch { sheetState.hide(); practiceItem = null }
                },
                onCancel = { scope.launch { sheetState.hide(); practiceItem = null } },
            )
        }
    }

    infoItem?.let { item ->
        val behaviorName = behaviors.firstOrNull { it.id == item.behaviorId }?.name
        AlertDialog(
            onDismissRequest = { infoItem = null },
            title = { Text("Step ${item.sortOrder + 1}: ${item.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (behaviorName != null) {
                        LabeledRow("Behavior", behaviorName)
                    }
                    LabeledRow("Distance", item.distanceLabel)
                    LabeledRow("Duration", item.durationLabel)
                    LabeledRow("Distraction", item.distractionLabel)
                    Text(
                        "This isn't your current step yet. Complete your current step to progress here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { infoItem = null }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun StepRow(
    item: TrainingPlanItem,
    isCurrent: Boolean,
    isLocked: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Step number badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    if (isCurrent) Color(0xFF43A047)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${item.sortOrder + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isLocked)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThreeDTag(item.distanceLabel)
                ThreeDTag(item.durationLabel)
                ThreeDTag(item.distractionLabel)
            }
        }

        if (isLocked) {
            Icon(
                Icons.Filled.Info,
                contentDescription = "Locked step",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        } else {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "Practice step",
                tint = if (isCurrent) Color(0xFF43A047)
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun LabeledRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
