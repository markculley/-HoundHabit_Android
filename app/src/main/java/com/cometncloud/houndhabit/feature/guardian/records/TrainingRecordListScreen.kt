package com.cometncloud.houndhabit.feature.guardian.records

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.CircularProgressIndicator
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
import com.cometncloud.houndhabit.core.models.TrainingRecord
import com.cometncloud.houndhabit.core.models.label
import com.cometncloud.houndhabit.shared.components.StatusBadge
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingRecordListScreen(
    viewModel: TrainingRecordViewModel = viewModel(),
    /** When non-null, the list is filtered to this pet and a back arrow is shown. */
    petFilter: PetFilter? = null,
    onBack: (() -> Unit)? = null,
    onRecordClick: (TrainingRecord) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showLogSheet by remember { mutableStateOf(false) }

    LaunchedEffect(petFilter?.petId) { viewModel.loadRecords(petFilter?.petId) }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage
        if (msg != null) {
            snackbar.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(petFilter?.let { "${it.petName}'s Sessions" } ?: "Sessions") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showLogSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Log session")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.records.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.records.isEmpty() -> EmptyState(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.records, key = { it.id }) { record ->
                        TrainingRecordRow(
                            record = record,
                            petName = if (petFilter == null) state.petNames[record.petId] else null,
                            onClick = { onRecordClick(record) },
                            onShareToggle = { viewModel.toggleSharing(record) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showLogSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLogSheet = false },
            sheetState = sheetState,
        ) {
            TrainingRecordFormScreen(
                pets = state.pets,
                preselectedPetId = petFilter?.petId,
                editing = null,
                isSaving = state.isLoading,
                onCreate = { petId, recordedAt, score, distance, distraction, duration, notes, isShared ->
                    viewModel.createRecord(
                        petId = petId,
                        recordedAt = recordedAt,
                        score = score,
                        distance = distance,
                        distraction = distraction,
                        duration = duration,
                        notes = notes,
                        isShared = isShared,
                    )
                    scope.launch {
                        sheetState.hide()
                        showLogSheet = false
                    }
                },
                onUpdate = { /* not used in create mode */ },
                onCancel = {
                    scope.launch {
                        sheetState.hide()
                        showLogSheet = false
                    }
                },
            )
        }
    }
}

data class PetFilter(val petId: String, val petName: String)

@Composable
private fun TrainingRecordRow(
    record: TrainingRecord,
    petName: String?,
    onClick: () -> Unit,
    onShareToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(status = record.status, size = 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(formatDate(record.recordedAt), style = MaterialTheme.typography.titleMedium)
            val subtitle = buildString {
                if (petName != null) {
                    append(petName)
                    append(" · ")
                }
                append(record.distance.label)
                append(" · ")
                append(record.distraction.label)
                append(" · ")
                append(record.duration.label)
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onShareToggle) {
            Icon(
                imageVector = Icons.Filled.PeopleAlt,
                contentDescription = if (record.isShared) "Unshare" else "Share",
                tint = if (record.isShared)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        Text("No Sessions Yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(4.dp))
        Text(
            "Tap + to record your first training session.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDate(instant: Instant): String {
    val tz = TimeZone.currentSystemDefault()
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).apply {
        timeZone = java.util.TimeZone.getTimeZone(tz.id)
    }
    return df.format(Date(instant.toEpochMilliseconds()))
}
