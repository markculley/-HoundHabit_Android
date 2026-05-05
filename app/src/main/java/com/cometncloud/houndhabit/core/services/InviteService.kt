package com.cometncloud.houndhabit.core.services

import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Invite
import com.cometncloud.houndhabit.core.models.InviteStatus
import com.cometncloud.houndhabit.core.models.LinkedGuardian
import com.cometncloud.houndhabit.core.models.LinkedTrainer
import com.cometncloud.houndhabit.core.models.TrainerGuardianLink
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class InviteService {
    private val supabase get() = SupabaseClient.client

    // ---- Trainer ----------------------------------------------------------

    /**
     * Creates an invite for [email] from the current trainer.
     *
     * iOS additionally fires a fire-and-forget Edge Function call to
     * `send-invite-email`. The Android port skips that side effect for now —
     * the trainer UI shows the code with a Copy button so they can share
     * manually. Email automation is a Phase 12 polish task.
     */
    suspend fun createInvite(email: String): Invite {
        val trainerId = supabase.auth.currentUserOrNull()?.id
            ?: throw InviteException.NotAuthenticated
        val payload = InviteInsert(
            trainerId = trainerId,
            email = email,
            code = generateCode(),
        )
        return supabase.postgrest
            .from("invites")
            .insert(payload) { select() }
            .decodeSingle()
    }

    suspend fun fetchInvites(): List<Invite> {
        val trainerId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
        return supabase.postgrest
            .from("invites")
            .select {
                filter { eq("trainer_id", trainerId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()
    }

    suspend fun fetchLinkedGuardians(): List<LinkedGuardian> {
        val trainerId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
        return supabase.postgrest
            .from("trainer_guardian_links")
            .select(
                columns = Columns.raw(
                    "*, profiles!trainer_guardian_links_guardian_id_fkey(*)",
                ),
            ) {
                filter {
                    eq("trainer_id", trainerId)
                    eq("status", "active")
                }
            }
            .decodeList()
    }

    // ---- Guardian ---------------------------------------------------------

    suspend fun fetchLinkedTrainer(): LinkedTrainer? {
        val guardianId = supabase.auth.currentUserOrNull()?.id ?: return null
        val results: List<LinkedTrainer> = supabase.postgrest
            .from("trainer_guardian_links")
            .select(
                columns = Columns.raw(
                    "*, profiles!trainer_guardian_links_trainer_id_fkey(*)",
                ),
            ) {
                filter {
                    eq("guardian_id", guardianId)
                    eq("status", "active")
                }
            }
            .decodeList()
        return results.firstOrNull()
    }

    /**
     * Looks up a pending, non-expired invite by code.
     * Throws [InviteException.InvalidCode], [InviteException.Expired], or
     * [InviteException.AlreadyUsed] on bad codes.
     */
    suspend fun fetchInviteByCode(code: String): Invite {
        val results: List<Invite> = supabase.postgrest
            .from("invites")
            .select { filter { eq("code", code.uppercase()) } }
            .decodeList()

        val invite = results.firstOrNull() ?: throw InviteException.InvalidCode
        when (invite.status) {
            InviteStatus.Expired -> throw InviteException.Expired
            InviteStatus.Accepted -> throw InviteException.AlreadyUsed
            InviteStatus.Pending -> Unit
        }
        if (invite.expiresAt != null && invite.expiresAt < Clock.System.now()) {
            throw InviteException.Expired
        }
        return invite
    }

    /**
     * Accepts the invite: creates a `trainer_guardian_links` row and marks the
     * invite as accepted.
     */
    suspend fun acceptInvite(invite: Invite): TrainerGuardianLink {
        val guardianId = supabase.auth.currentUserOrNull()?.id
            ?: throw InviteException.NotAuthenticated

        val link: TrainerGuardianLink = supabase.postgrest
            .from("trainer_guardian_links")
            .insert(
                TrainerGuardianLinkInsert(
                    trainerId = invite.trainerId,
                    guardianId = guardianId,
                ),
            ) { select() }
            .decodeSingle()

        supabase.postgrest
            .from("invites")
            .update({ set("status", "accepted") }) {
                filter { eq("id", invite.id) }
            }

        return link
    }

    // ---- Helpers ----------------------------------------------------------

    /** 8-char invite code from a confusion-free alphabet (no O, 0, I, 1). */
    private fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(8) {
            repeat(8) { append(chars.random()) }
        }
    }
}

sealed class InviteException(message: String) : Exception(message) {
    object InvalidCode : InviteException("Invalid code. Please check and try again.")
    object Expired : InviteException("This code has expired.")
    object AlreadyUsed : InviteException("This code has already been used.")
    object NotAuthenticated : InviteException("You must be signed in to perform this action.")
}

@Serializable
private data class InviteInsert(
    @SerialName("trainer_id") val trainerId: String,
    val email: String,
    val code: String,
)

@Serializable
private data class TrainerGuardianLinkInsert(
    @SerialName("trainer_id") val trainerId: String,
    @SerialName("guardian_id") val guardianId: String,
)
