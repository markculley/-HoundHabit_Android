package com.cometncloud.houndhabit.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Invite(
    val id: String,
    @SerialName("trainer_id") val trainerId: String,
    val email: String,
    val code: String,
    val status: InviteStatus,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("expires_at") val expiresAt: Instant? = null,
)

@Serializable
enum class InviteStatus {
    @SerialName("pending") Pending,
    @SerialName("accepted") Accepted,
    @SerialName("expired") Expired,
}
