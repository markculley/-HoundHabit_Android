package com.cometncloud.houndhabit.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Resource(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("added_by_id") val addedById: String,
    @SerialName("guardian_id") val guardianId: String,
    val kind: ResourceKind,
    val url: String? = null,
    val body: String? = null,
    val title: String,
    @SerialName("created_at") val createdAt: Instant? = null,
)

@Serializable
enum class ResourceKind {
    @SerialName("photo") Photo,
    @SerialName("url") Url,
    @SerialName("note") Note,
}

val ResourceKind.label: String
    get() = when (this) {
        ResourceKind.Photo -> "Photo"
        ResourceKind.Url -> "URL"
        ResourceKind.Note -> "Note"
    }
