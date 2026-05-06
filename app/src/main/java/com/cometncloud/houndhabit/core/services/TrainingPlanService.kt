package com.cometncloud.houndhabit.core.services

import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.TrainingPlan
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Phase 9a: trainer plan CRUD only. Behaviors, items, assignments, and the
 * guardian-side fetchAssignedPlans / advancement helpers arrive in 9b/9c.
 */
class TrainingPlanService {
    private val supabase get() = SupabaseClient.client

    suspend fun fetchPlans(): List<TrainingPlan> {
        val trainerId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
        return supabase.postgrest
            .from("training_plans")
            .select {
                filter { eq("trainer_id", trainerId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()
    }

    suspend fun createPlan(title: String, description: String?): TrainingPlan {
        val trainerId = supabase.auth.currentUserOrNull()?.id
            ?: throw PlanException.NotAuthenticated
        val payload = PlanInsert(
            trainerId = trainerId,
            title = title,
            description = description,
        )
        return supabase.postgrest
            .from("training_plans")
            .insert(payload) { select() }
            .decodeSingle()
    }

    suspend fun updatePlan(plan: TrainingPlan): TrainingPlan {
        val payload = PlanUpdate(title = plan.title, description = plan.description)
        return supabase.postgrest
            .from("training_plans")
            .update(payload) {
                select()
                filter { eq("id", plan.id) }
            }
            .decodeSingle()
    }

    suspend fun deletePlan(id: String) {
        supabase.postgrest
            .from("training_plans")
            .delete { filter { eq("id", id) } }
    }
}

sealed class PlanException(message: String) : Exception(message) {
    object NotAuthenticated : PlanException("You must be signed in to perform this action.")
    object AlreadyAssigned : PlanException("This guardian is already assigned to this plan.")
}

@Serializable
private data class PlanInsert(
    @SerialName("trainer_id") val trainerId: String,
    val title: String,
    val description: String?,
)

@Serializable
private data class PlanUpdate(
    val title: String,
    val description: String?,
)
