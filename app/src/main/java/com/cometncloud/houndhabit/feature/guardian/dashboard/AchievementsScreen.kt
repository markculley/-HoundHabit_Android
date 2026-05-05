package com.cometncloud.houndhabit.feature.guardian.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cometncloud.houndhabit.core.models.Badge
import com.cometncloud.houndhabit.core.models.BadgeType
import com.cometncloud.houndhabit.core.models.description
import com.cometncloud.houndhabit.core.models.title
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.text.DateFormat
import java.util.Date

/**
 * Full badge gallery — shown inside a `ModalBottomSheet` from the dashboard.
 * Renders one row per [BadgeType]: earned (colored, with date) or locked (greyed).
 */
@Composable
fun AchievementsContent(earnedBadges: List<Badge>) {
    val earnedByType: Map<BadgeType, Badge> = earnedBadges.associateBy { it.badgeType }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Achievements", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(BadgeType.entries.toList()) { type ->
                AchievementRow(type = type, earned = earnedByType[type])
                HorizontalDivider()
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun AchievementRow(type: BadgeType, earned: Badge?) {
    val isEarned = earned != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = if (isEarned) type.color.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                tint = if (isEarned) type.color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                type.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isEarned)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                type.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (earned != null) {
                Text(
                    "Earned ${formatEarnedDate(earned.earnedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = type.color,
                )
            }
        }
    }
}

private fun formatEarnedDate(instant: Instant): String {
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = java.util.TimeZone.getTimeZone(TimeZone.currentSystemDefault().id)
    }
    return df.format(Date(instant.toEpochMilliseconds()))
}
