package com.cometncloud.houndhabit.feature.guardian.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import android.provider.Settings as AndroidSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.core.content.ContextCompat
import com.cometncloud.houndhabit.core.models.LinkedTrainer
import com.cometncloud.houndhabit.core.services.InviteService
import com.cometncloud.houndhabit.shared.notifications.DailyReminderPrefs
import com.cometncloud.houndhabit.shared.notifications.DailyReminderScheduler
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.text.DateFormat
import java.util.Date

private const val PRIVACY_POLICY_URL = "https://www.cometncloud.com/houndhabitprivacypolicy"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isTrainer: Boolean,
    onSignOut: () -> Unit,
    onEnterInviteCode: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var linkedTrainer by remember { mutableStateOf<LinkedTrainer?>(null) }
    val service = remember { InviteService() }

    LaunchedEffect(isTrainer) {
        if (!isTrainer) {
            runCatching { service.fetchLinkedTrainer() }.getOrNull()?.let {
                linkedTrainer = it
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CardSection(label = "Account") {
                Text(
                    "Profile editing arrives in Phase 12.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardSection(label = "Notifications") {
                DailyReminderControls()
            }

            if (!isTrainer) {
                CardSection(label = "Trainer") {
                    val trainer = linkedTrainer
                    if (trainer != null) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Linked Trainer",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    trainer.profile.fullName ?: "Trainer",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val linkedAt = trainer.linkedAt
                            if (linkedAt != null) {
                                Text(
                                    "Linked ${formatDate(linkedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onEnterInviteCode,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Enter Invite Code") }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Privacy Policy",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TextButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign Out", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CardSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(16.dp),
        ) { content() }
    }
}

private fun formatDate(instant: Instant): String {
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = java.util.TimeZone.getTimeZone(TimeZone.currentSystemDefault().id)
    }
    return df.format(Date(instant.toEpochMilliseconds()))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyReminderControls() {
    val context = LocalContext.current
    val prefs = remember { DailyReminderPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.isEnabled) }
    var hour by remember { mutableStateOf(prefs.hour) }
    var minute by remember { mutableStateOf(prefs.minute) }
    var showPicker by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionDenied = false
            enabled = true
            prefs.isEnabled = true
            DailyReminderScheduler.schedule(context, hour, minute)
        } else {
            permissionDenied = true
            enabled = false
            prefs.isEnabled = false
        }
    }

    // Re-check permission each time the user returns from system settings.
    LaunchedEffect(Unit) {
        permissionDenied = enabled && !hasPermission()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Remind me to train", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Daily nudge at your chosen time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { wantOn ->
                    if (wantOn) {
                        if (hasPermission()) {
                            enabled = true
                            prefs.isEnabled = true
                            DailyReminderScheduler.schedule(context, hour, minute)
                        } else {
                            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        enabled = false
                        prefs.isEnabled = false
                        permissionDenied = false
                        DailyReminderScheduler.cancel(context)
                    }
                },
            )
        }

        if (enabled) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Time",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = { showPicker = true }) {
                    Text(formatTime(hour, minute))
                }
            }
        }

        if (permissionDenied) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Notifications are blocked for Hound Habit.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                ) { Text("Open System Settings") }
            }
        }

    }

    if (showPicker) {
        val pickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = pickerState.hour
                    minute = pickerState.minute
                    prefs.hour = hour
                    prefs.minute = minute
                    if (enabled) DailyReminderScheduler.schedule(context, hour, minute)
                    showPicker = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
            title = { Text("Reminder time") },
            text = { TimePicker(state = pickerState) },
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
    }
    val df = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return df.format(cal.time)
}
