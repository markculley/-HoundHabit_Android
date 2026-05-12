package com.cometncloud.houndhabit.feature.guardian.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cometncloud.houndhabit.core.services.AssignedPlan

/**
 * Pet-detail sheet for linking an existing assignment to this pet. Includes
 * both the guardian's own plans (linked elsewhere or not at all) and trainer-
 * assigned plans the trainer left as "Any pet". On tap, [onPick] fires with
 * that AssignedPlan; the caller is expected to call updateAssignmentPet.
 */
@Composable
fun AssignExistingPlanSheet(
    candidates: List<AssignedPlan>,
    isOwnPlan: (AssignedPlan) -> Boolean,
    onPick: (AssignedPlan) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Assign Existing Plan", style = MaterialTheme.typography.headlineSmall)

        if (candidates.isEmpty()) {
            Text(
                "No plans available — create a new one with the “New Plan” option.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            ) {
                items(candidates, key = { it.id }) { ap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(ap) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(ap.plan.title, style = MaterialTheme.typography.bodyLarge)
                            if (!isOwnPlan(ap)) {
                                Text(
                                    "From your trainer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            val desc = ap.plan.description
                            if (!desc.isNullOrBlank()) {
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
