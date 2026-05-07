package com.cometncloud.houndhabit.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Behavior(
    val id: String,
    @SerialName("plan_id") val planId: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("created_at") val createdAt: Instant? = null,
)
