package com.cometncloud.houndhabit.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrainingPlanItem(
    val id: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("behavior_id") val behaviorId: String? = null,
    @SerialName("sort_order") val sortOrder: Int,
    val title: String,
    val distance: Distance,
    val duration: TrainingDuration,
    val distraction: Distraction,
    @SerialName("distance_custom") val distanceCustom: String? = null,
    @SerialName("duration_custom") val durationCustom: String? = null,
    @SerialName("distraction_custom") val distractionCustom: String? = null,
)

val TrainingPlanItem.distanceLabel: String
    get() = distance.displayLabel(distanceCustom)
val TrainingPlanItem.durationLabel: String
    get() = duration.displayLabel(durationCustom)
val TrainingPlanItem.distractionLabel: String
    get() = distraction.displayLabel(distractionCustom)
