package com.cometncloud.houndhabit.feature.guardian.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Comment
import com.cometncloud.houndhabit.core.models.TrainingRecord
import com.cometncloud.houndhabit.core.models.label
import com.cometncloud.houndhabit.core.services.CommentService
import com.cometncloud.houndhabit.shared.components.StatusBadge
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.text.DateFormat
import java.util.Date

/**
 * Guardian-side record detail. Looks up the record from the shared
 * [TrainingRecordViewModel], wires edit/delete/share, and self-loads any
 * trainer comments so they show up read-only.
 */
@Composable
fun TrainingRecordDetailScreen(
    recordId: String,
    viewModel: TrainingRecordViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val record = state.records.firstOrNull { it.id == recordId }
    val petName = record?.let { state.petNames[it.petId] } ?: "Unknown Pet"
    val currentUserId = remember { SupabaseClient.client.auth.currentUserOrNull()?.id }

    LaunchedEffect(record == null) {
        // If the record is gone (deleted), pop back.
        if (record == null && state.records.isNotEmpty()) onBack()
    }
    if (record == null) return

    // Guardian self-loads comments so they can read trainer comments. The
    // closure-injection pattern below leaves `onAddComment` null, which keeps
    // the input row hidden — guardian view is read-only for the thread.
    val commentService = remember { CommentService() }
    var comments by remember(record.id) { mutableStateOf<List<Comment>>(emptyList()) }
    LaunchedEffect(record.id) {
        runCatching { commentService.fetchComments(record.id) }
            .getOrNull()
            ?.let { comments = it }
    }

    RecordDetailContent(
        record = record,
        petName = petName,
        isLoading = state.isLoading,
        errorMessage = state.errorMessage,
        onClearError = viewModel::clearError,
        isReadOnly = false,
        onShareToggle = { viewModel.toggleSharing(record) },
        onEdit = null, // edit happens via the inline sheet inside content
        onDelete = { viewModel.deleteRecord(record); onBack() },
        useEditSheet = true,
        viewModelForEditSheet = viewModel,
        comments = comments,
        currentUserId = currentUserId,
        onAddComment = null,
        onDeleteComment = null,
        onBack = onBack,
    )
}

/**
 * Trainer-side record detail. Read-only on the record itself; the comment
 * thread renders with input + own-comment delete via injected closures.
 */
@Composable
fun TrainerRecordDetailScreen(
    record: TrainingRecord,
    petName: String,
    comments: List<Comment>,
    currentUserId: String?,
    onAddComment: suspend (String) -> Unit,
    onDeleteComment: suspend (Comment) -> Unit,
    onLoadComments: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(record.id) { onLoadComments() }

    RecordDetailContent(
        record = record,
        petName = petName,
        isLoading = false,
        errorMessage = null,
        onClearError = {},
        isReadOnly = true,
        onShareToggle = null,
        onEdit = null,
        onDelete = null,
        useEditSheet = false,
        viewModelForEditSheet = null,
        comments = comments,
        currentUserId = currentUserId,
        onAddComment = onAddComment,
        onDeleteComment = onDeleteComment,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailContent(
    record: TrainingRecord,
    petName: String,
    isLoading: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    isReadOnly: Boolean,
    onShareToggle: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    useEditSheet: Boolean,
    viewModelForEditSheet: TrainingRecordViewModel?,
    comments: List<Comment>,
    currentUserId: String?,
    onAddComment: (suspend (String) -> Unit)?,
    onDeleteComment: (suspend (Comment) -> Unit)?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbar.showSnackbar(errorMessage)
            onClearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatDay(record.recordedAt)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isReadOnly) {
                        TextButton(onClick = {
                            if (useEditSheet) showEditSheet = true else onEdit?.invoke()
                        }) { Text("Edit") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DetailSection("Pet") {
                Text(petName, style = MaterialTheme.typography.bodyLarge)
            }

            DetailSection("Status") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = record.status, size = 24.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(record.status.label, style = MaterialTheme.typography.titleMedium)
                }
            }

            DetailSection("Date") {
                Text(formatLong(record.recordedAt), style = MaterialTheme.typography.bodyLarge)
            }

            DetailSection("Three D's") {
                LabeledValue("Distance", record.distance.label)
                LabeledValue("Distraction", record.distraction.label)
                LabeledValue("Duration", record.duration.label)
            }

            val notes = record.notes
            if (!notes.isNullOrBlank()) {
                DetailSection("Notes") {
                    Text(notes, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (!isReadOnly && onShareToggle != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Share with Trainer",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = record.isShared,
                        onCheckedChange = { onShareToggle() },
                    )
                }
            }

            HorizontalDivider()

            CommentThread(
                comments = comments,
                currentUserId = currentUserId,
                onAddComment = onAddComment,
                onDeleteComment = onDeleteComment,
            )

            if (!isReadOnly && onDelete != null) {
                HorizontalDivider()
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete Session", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showEditSheet && useEditSheet && viewModelForEditSheet != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            sheetState = sheetState,
        ) {
            TrainingRecordFormScreen(
                pets = viewModelForEditSheet.state.value.pets,
                preselectedPetId = record.petId,
                editing = record,
                isSaving = isLoading,
                onCreate = { _, _, _, _, _, _, _, _ -> /* not used in edit mode */ },
                onUpdate = { updated ->
                    viewModelForEditSheet.updateRecord(updated)
                    scope.launch {
                        sheetState.hide()
                        showEditSheet = false
                    }
                },
                onCancel = {
                    scope.launch {
                        sheetState.hide()
                        showEditSheet = false
                    }
                },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this session?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDay(instant: Instant): String {
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = java.util.TimeZone.getTimeZone(TimeZone.currentSystemDefault().id)
    }
    return df.format(Date(instant.toEpochMilliseconds()))
}

private fun formatLong(instant: Instant): String {
    val df = DateFormat.getDateInstance(DateFormat.LONG).apply {
        timeZone = java.util.TimeZone.getTimeZone(TimeZone.currentSystemDefault().id)
    }
    return df.format(Date(instant.toEpochMilliseconds()))
}
