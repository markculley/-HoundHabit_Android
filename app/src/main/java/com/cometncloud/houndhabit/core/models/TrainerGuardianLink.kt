package com.cometncloud.houndhabit.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrainerGuardianLink(
    val id: String,
    @SerialName("trainer_id") val trainerId: String,
    @SerialName("guardian_id") val guardianId: String,
    val status: LinkStatus,
    @SerialName("linked_at") val linkedAt: Instant? = null,
)

@Serializable
enum class LinkStatus {
    @SerialName("active") Active,
    @SerialName("inactive") Inactive,
}

/**
 * Guardian's view of a linked trainer.
 *
 * Decoded from the embedded select
 * `select("*, profiles!trainer_guardian_links_trainer_id_fkey(*)")` —
 * Supabase keys the embed by the joined table name (`profiles`).
 */
@Serializable
data class LinkedTrainer(
    val id: String,
    @SerialName("trainer_id") val trainerId: String,
    @SerialName("guardian_id") val guardianId: String,
    val status: LinkStatus,
    @SerialName("linked_at") val linkedAt: Instant? = null,
    @SerialName("profiles") val profile: Profile,
)

/** Trainer's view of a linked guardian — symmetric to [LinkedTrainer]. */
@Serializable
data class LinkedGuardian(
    val id: String,
    @SerialName("trainer_id") val trainerId: String,
    @SerialName("guardian_id") val guardianId: String,
    val status: LinkStatus,
    @SerialName("linked_at") val linkedAt: Instant? = null,
    @SerialName("profiles") val profile: Profile,
)
