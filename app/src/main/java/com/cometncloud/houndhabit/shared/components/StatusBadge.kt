package com.cometncloud.houndhabit.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cometncloud.houndhabit.core.models.TrainingStatus

/** Small coloured circle representing a training-session status. */
@Composable
fun StatusBadge(status: TrainingStatus, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(status.color, CircleShape),
    )
}

val TrainingStatus.color: Color
    get() = when (this) {
        TrainingStatus.Red -> Color(0xFFE53935)
        TrainingStatus.Orange -> Color(0xFFFB8C00)
        TrainingStatus.Yellow -> Color(0xFFFDD835)
        TrainingStatus.Green -> Color(0xFF43A047)
    }
