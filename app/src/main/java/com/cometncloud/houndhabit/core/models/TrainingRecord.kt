package com.cometncloud.houndhabit.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrainingRecord(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("guardian_id") val guardianId: String,
    @SerialName("recorded_at") val recordedAt: Instant,
    val status: TrainingStatus,
    val distance: Distance,
    val distraction: Distraction,
    val duration: TrainingDuration,
    @SerialName("distance_custom") val distanceCustom: String? = null,
    @SerialName("duration_custom") val durationCustom: String? = null,
    @SerialName("distraction_custom") val distractionCustom: String? = null,
    val notes: String? = null,
    @SerialName("is_shared") val isShared: Boolean = false,
    val score: Int,
    @SerialName("plan_item_id") val planItemId: String? = null,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
)

@Serializable
enum class TrainingStatus {
    @SerialName("red") Red,
    @SerialName("orange") Orange,
    @SerialName("yellow") Yellow,
    @SerialName("green") Green;

    companion object {
        /**
         * Derives status from a 0–5 rep score.
         * Green (5/5) advance, Yellow (3–4/5) / Orange (2/5) stay, Red (0–1/5) drop back.
         */
        fun fromScore(score: Int): TrainingStatus = when (score) {
            5 -> Green
            3, 4 -> Yellow
            2 -> Orange
            else -> Red
        }
    }
}

@Serializable
enum class Distance {
    @SerialName("arms_length") ArmsLength,
    @SerialName("6_feet") SixFeet,
    @SerialName("12_feet") TwelveFeet,
    @SerialName("20_feet") TwentyFeet,
    @SerialName("20_plus_feet") TwentyPlusFeet,
    @SerialName("custom") Custom,
}

@Serializable
enum class Distraction {
    @SerialName("none") None,
    @SerialName("any") Any,
    @SerialName("custom") Custom,
}

@Serializable
enum class TrainingDuration {
    @SerialName("instant") Instant,
    @SerialName("5_seconds") FiveSeconds,
    @SerialName("5_plus_seconds") FivePlusSeconds,
    @SerialName("custom") Custom,
}

val Distance.label: String
    get() = when (this) {
        Distance.ArmsLength -> "Arm's length"
        Distance.SixFeet -> "6 ft"
        Distance.TwelveFeet -> "12 ft"
        Distance.TwentyFeet -> "20 ft"
        Distance.TwentyPlusFeet -> "20+ ft"
        Distance.Custom -> "Custom"
    }

val Distraction.label: String
    get() = when (this) {
        Distraction.None -> "None"
        Distraction.Any -> "Any"
        Distraction.Custom -> "Custom"
    }

val TrainingDuration.label: String
    get() = when (this) {
        TrainingDuration.Instant -> "Instant"
        TrainingDuration.FiveSeconds -> "5 sec"
        TrainingDuration.FivePlusSeconds -> "5+ sec"
        TrainingDuration.Custom -> "Custom"
    }

val TrainingStatus.label: String
    get() = when (this) {
        TrainingStatus.Red -> "Not getting it"
        TrainingStatus.Orange -> "Occasional success"
        TrainingStatus.Yellow -> "Halfway there"
        TrainingStatus.Green -> "Success"
    }

fun Distance.displayLabel(custom: String?): String =
    if (this == Distance.Custom) custom ?: "Custom" else label

fun Distraction.displayLabel(custom: String?): String =
    if (this == Distraction.Custom) custom ?: "Custom" else label

fun TrainingDuration.displayLabel(custom: String?): String =
    if (this == TrainingDuration.Custom) custom ?: "Custom" else label
