package com.cometncloud.houndhabit.core.services

import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Pet
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PetService {
    private val supabase get() = SupabaseClient.client

    suspend fun fetchPets(guardianId: String): List<Pet> =
        supabase.postgrest
            .from("pets")
            .select {
                filter { eq("guardian_id", guardianId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    suspend fun createPet(
        guardianId: String,
        name: String,
        breed: String?,
        photoUrl: String?,
    ): Pet {
        val payload = PetInsert(
            guardianId = guardianId,
            name = name,
            breed = breed,
            photoUrl = photoUrl,
        )
        return supabase.postgrest
            .from("pets")
            .insert(payload) { select() }
            .decodeSingle()
    }

    suspend fun updatePet(pet: Pet): Pet {
        val payload = PetUpdate(name = pet.name, breed = pet.breed, photoUrl = pet.photoUrl)
        return supabase.postgrest
            .from("pets")
            .update(payload) {
                select()
                filter { eq("id", pet.id) }
            }
            .decodeSingle()
    }

    suspend fun deletePet(id: String) {
        supabase.postgrest
            .from("pets")
            .delete { filter { eq("id", id) } }
    }
}

@Serializable
private data class PetInsert(
    @SerialName("guardian_id") val guardianId: String,
    val name: String,
    val breed: String?,
    @SerialName("photo_url") val photoUrl: String?,
)

@Serializable
private data class PetUpdate(
    val name: String,
    val breed: String?,
    @SerialName("photo_url") val photoUrl: String?,
)
