package com.cometncloud.houndhabit.core.services

import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Comment
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CommentService {
    private val supabase get() = SupabaseClient.client

    suspend fun fetchComments(recordId: String): List<Comment> =
        supabase.postgrest
            .from("comments")
            .select {
                filter { eq("training_record_id", recordId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    suspend fun addComment(recordId: String, body: String): Comment {
        val authorId = supabase.auth.currentUserOrNull()?.id
            ?: throw CommentException.NotAuthenticated
        val payload = CommentInsert(
            trainingRecordId = recordId,
            authorId = authorId,
            body = body,
        )
        return supabase.postgrest
            .from("comments")
            .insert(payload) { select() }
            .decodeSingle()
    }

    suspend fun deleteComment(id: String) {
        supabase.postgrest
            .from("comments")
            .delete { filter { eq("id", id) } }
    }
}

sealed class CommentException(message: String) : Exception(message) {
    object NotAuthenticated : CommentException("You must be signed in to post a comment.")
}

@Serializable
private data class CommentInsert(
    @SerialName("training_record_id") val trainingRecordId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
)
