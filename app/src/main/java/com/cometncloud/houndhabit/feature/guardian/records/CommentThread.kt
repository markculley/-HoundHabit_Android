package com.cometncloud.houndhabit.feature.guardian.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cometncloud.houndhabit.core.models.Comment
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.text.DateFormat
import java.util.Date

/**
 * Role-agnostic comment thread.
 *
 * Closure-injection pattern matches iOS `TrainingRecordDetailView`:
 * - `onAddComment != null` → render the input row (trainer side).
 * - `onDeleteComment != null` and `comment.authorId == currentUserId` → show
 *   the trash affordance (own comments only).
 * - Both null → fully read-only thread.
 */
@Composable
fun CommentThread(
    comments: List<Comment>,
    currentUserId: String?,
    onAddComment: (suspend (String) -> Unit)? = null,
    onDeleteComment: (suspend (Comment) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "COMMENTS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (comments.isEmpty()) {
            Text(
                "No comments yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                comments.forEach { c ->
                    val isOwn = currentUserId != null && c.authorId == currentUserId
                    CommentRow(
                        comment = c,
                        isOwn = isOwn,
                        onDelete = if (isOwn && onDeleteComment != null) {
                            { onDeleteComment(c) }
                        } else null,
                    )
                    HorizontalDivider()
                }
            }
        }

        if (onAddComment != null) {
            CommentInputRow(onSend = onAddComment)
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    isOwn: Boolean,
    onDelete: (suspend () -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isOwn) "You" else "Trainer",
                    style = MaterialTheme.typography.labelLarge,
                )
                comment.createdAt?.let {
                    Text(
                        " · ${formatTimestamp(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
        if (onDelete != null) {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete comment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete comment?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (onDelete != null) scope.launch { onDelete() }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CommentInputRow(onSend: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Add a comment…") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            maxLines = 4,
        )
        IconButton(
            enabled = text.isNotBlank() && !isSending,
            onClick = {
                val body = text.trim()
                if (body.isEmpty()) return@IconButton
                scope.launch {
                    isSending = true
                    try {
                        onSend(body)
                        text = ""
                    } finally {
                        isSending = false
                    }
                }
            },
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send comment",
                )
            }
        }
    }
}

private fun formatTimestamp(instant: Instant): String {
    val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).apply {
        timeZone = java.util.TimeZone.getTimeZone(TimeZone.currentSystemDefault().id)
    }
    return df.format(Date(instant.toEpochMilliseconds()))
}
