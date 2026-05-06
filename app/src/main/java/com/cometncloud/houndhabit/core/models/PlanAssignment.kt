package com.cometncloud.houndhabit.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlanAssignment(
    val id: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("trainer_id") val trainerId: String,
    @SerialName("guardian_id") val guardianId: String,
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("assigned_at") val assignedAt: Instant? = null,
    @SerialName("current_item_id") val currentItemId: String? = null,
    /**
     * Whether the guardian has opted in to sharing session records logged
     * under this assignment with the trainer. Trainer-assigned plans default
     * to true; guardian self-assigned plans default to false.
     */
    @SerialName("is_shared") val isShared: Boolean = true,
)
