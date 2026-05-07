package com.cometncloud.houndhabit.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cometncloud.houndhabit.feature.trainer.plans.PlanProgress
import com.cometncloud.houndhabit.feature.trainer.plans.label

/**
 * Shared between trainer's plan-detail (per-assignment) and guardian's plan-list
 * (per assigned plan).
 */
@Composable
fun PlanProgressBadge(progress: PlanProgress, modifier: Modifier = Modifier) {
    val (fg, bg) = when (progress) {
        PlanProgress.ToDo -> MaterialTheme.colorScheme.onSurfaceVariant to
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        PlanProgress.InProgress -> Color(0xFFFB8C00) to Color(0xFFFB8C00).copy(alpha = 0.15f)
        PlanProgress.Done -> Color(0xFF43A047) to Color(0xFF43A047).copy(alpha = 0.15f)
    }
    Text(
        text = progress.label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier
            .background(bg, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
