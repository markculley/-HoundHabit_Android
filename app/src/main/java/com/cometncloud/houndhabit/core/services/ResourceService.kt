package com.cometncloud.houndhabit.core.services

import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Resource
import com.cometncloud.houndhabit.core.models.ResourceKind
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

class ResourceService {
    private val supabase get() = SupabaseClient.client

    suspend fun fetchResources(guardianId: String): List<Resource> =
        supabase.postgrest
            .from("resources")
            .select {
                filter { eq("guardian_id", guardianId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    /**
     * Guardian self-creates a resource. owner_id == added_by_id == guardianId.
     *
     * @param resourceId optional pre-generated id — caller provides one when uploading a photo
     *                   to storage so the storage path can be built before the row exists.
     */
    suspend fun createResource(
        guardianId: String,
        kind: ResourceKind,
        title: String,
        url: String?,
        body: String?,
        resourceId: String = UUID.randomUUID().toString(),
    ): Resource {
        val payload = ResourceInsert(
            id = resourceId,
            ownerId = guardianId,
            addedById = guardianId,
            guardianId = guardianId,
            kind = kind,
            url = url,
            body = body,
            title = title,
        )
        return supabase.postgrest
            .from("resources")
            .insert(payload) { select() }
            .decodeSingle()
    }

    /** Trainer creates a resource on a guardian's behalf — Phase 7+. */
    suspend fun createResourceForGuardian(
        guardianId: String,
        addedById: String,
        kind: ResourceKind,
        title: String,
        url: String?,
        body: String?,
        resourceId: String = UUID.randomUUID().toString(),
    ): Resource {
        val payload = ResourceInsert(
            id = resourceId,
            ownerId = guardianId,
            addedById = addedById,
            guardianId = guardianId,
            kind = kind,
            url = url,
            body = body,
            title = title,
        )
        return supabase.postgrest
            .from("resources")
            .insert(payload) { select() }
            .decodeSingle()
    }

    suspend fun deleteResource(id: String) {
        supabase.postgrest
            .from("resources")
            .delete { filter { eq("id", id) } }
    }
}

@Serializable
private data class ResourceInsert(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("added_by_id") val addedById: String,
    @SerialName("guardian_id") val guardianId: String,
    val kind: ResourceKind,
    val url: String?,
    val body: String?,
    val title: String,
)
