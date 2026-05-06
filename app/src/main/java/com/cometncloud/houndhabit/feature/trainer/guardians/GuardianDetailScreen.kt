package com.cometncloud.houndhabit.feature.trainer.guardians

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cometncloud.houndhabit.core.models.LinkedGuardian
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.models.TrainingRecord
import com.cometncloud.houndhabit.core.models.label
import com.cometncloud.houndhabit.shared.components.StatusBadge
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianDetailScreen(
    guardian: LinkedGuardian,
    viewModel: GuardianDetailViewModel,
    onBack: () -> Unit,
    onRecordClick: (TrainingRecord) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(guardian.id) { viewModel.load(guardian) }
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
                title = { Text(guardian.profile.fullName ?: "Guardian") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading && state.records.isEmpty() && state.pets.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { GuardianHeader(guardian) }
                    item { HorizontalDivider() }

                    if (state.pets.isNotEmpty()) {
                        item { SectionLabel("Pets") }
                        items(state.pets, key = { it.id }) { PetRow(it) }
                        item { HorizontalDivider() }
                    }

                    item { SectionLabel("Shared Sessions") }
                    if (state.records.isEmpty()) {
                        item {
                            Text(
                                "No shared sessions yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    } else {
                        items(state.records, key = { it.id }) { record ->
                            SessionRow(
                                record = record,
                                petName = viewModel.petName(record.petId),
                                onClick = { onRecordClick(record) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardianHeader(guardian: LinkedGuardian) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val linkedAt = guardian.linkedAt
        if (linkedAt != null) {
            Text(
                "Linked ${formatDate(linkedAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun PetRow(pet: Pet) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(pet.name, style = MaterialTheme.typography.bodyLarge)
        if (!pet.breed.isNullOrBlank()) {
            Text(
                " · ${pet.breed}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionRow(record: TrainingRecord, petName: String, onClick: () -> Unit) {
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
            Text(formatDateTime(record.recordedAt), style = MaterialTheme.typography.titleMedium)
            Text(
                "$petName · ${record.distance.label} · ${record.distraction.label} · ${record.duration.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDate(instant: Instant): String {
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = java.util.TimeZone.getTimeZone(TimeZone.currentSystemDefault().id)
    }
    return df.format(Date(instant.toEpochMilliseconds()))
}

private fun formatDateTime(instant: Instant): String {
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).apply {
        timeZone = java.util.TimeZone.getTimeZone(TimeZone.currentSystemDefault().id)
    }
    return df.format(Date(instant.toEpochMilliseconds()))
}
