package com.cometncloud.houndhabit.core.services

import com.cometncloud.houndhabit.core.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType

class StorageService {
    private val supabase get() = SupabaseClient.client

    /**
     * Uploads JPEG bytes to the `pet-photos` bucket and returns the public URL.
     *
     * Path layout is `{guardianId}/{petId}/photo.jpg` with both UUIDs lowercased
     * because storage RLS compares the first folder against `auth.uid()::text`,
     * which Supabase emits in lowercase.
     */
    suspend fun uploadPetPhoto(bytes: ByteArray, guardianId: String, petId: String): String {
        val path = "${guardianId.lowercase()}/${petId.lowercase()}/photo.jpg"
        supabase.storage.from("pet-photos").upload(path, bytes) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }
        return supabase.storage.from("pet-photos").publicUrl(path)
    }

    suspend fun uploadResourcePhoto(bytes: ByteArray, guardianId: String, resourceId: String): String {
        val path = "${guardianId.lowercase()}/${resourceId.lowercase()}.jpg"
        supabase.storage.from("resources").upload(path, bytes) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }
        return supabase.storage.from("resources").publicUrl(path)
    }
}
