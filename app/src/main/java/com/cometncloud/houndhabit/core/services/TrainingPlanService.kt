package com.cometncloud.houndhabit.core.services

import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Behavior
import com.cometncloud.houndhabit.core.models.Distance
import com.cometncloud.houndhabit.core.models.Distraction
import com.cometncloud.houndhabit.core.models.PlanAssignment
import com.cometncloud.houndhabit.core.models.TrainingDuration
import com.cometncloud.houndhabit.core.models.TrainingPlan
import com.cometncloud.houndhabit.core.models.TrainingPlanItem
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TrainingPlanService {
    private val supabase get() = SupabaseClient.client

    // ---- Plans -----------------------------------------------------------

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

    // ---- Behaviors -------------------------------------------------------

    suspend fun fetchBehaviors(planId: String): List<Behavior> =
        supabase.postgrest
            .from("behaviors")
            .select {
                filter { eq("plan_id", planId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun createBehavior(planId: String, name: String, sortOrder: Int): Behavior {
        val payload = BehaviorInsert(planId = planId, name = name, sortOrder = sortOrder)
        return supabase.postgrest
            .from("behaviors")
            .insert(payload) { select() }
            .decodeSingle()
    }

    suspend fun updateBehavior(behavior: Behavior): Behavior {
        val payload = BehaviorUpdate(name = behavior.name, sortOrder = behavior.sortOrder)
        return supabase.postgrest
            .from("behaviors")
            .update(payload) {
                select()
                filter { eq("id", behavior.id) }
            }
            .decodeSingle()
    }

    suspend fun deleteBehavior(id: String) {
        supabase.postgrest
            .from("behaviors")
            .delete { filter { eq("id", id) } }
    }

    /**
     * Persists new sort_order values after a drag-reorder. Per-row UPDATE
     * (vs delete+reinsert) so we don't churn primary keys.
     *
     * Two-pass: a UNIQUE(plan_id, sort_order) constraint will reject any
     * sequential single-pass scheme that swaps two rows, since the first
     * UPDATE collides with the row that still holds that sort_order. We
     * first stash everyone in a high range, then stamp the final values.
     */
    suspend fun reorderBehaviors(behaviors: List<Behavior>) {
        for ((idx, b) in behaviors.withIndex()) {
            supabase.postgrest
                .from("behaviors")
                .update(BehaviorUpdate(name = b.name, sortOrder = SORT_STASH_BASE + idx)) {
                    filter { eq("id", b.id) }
                }
        }
        for ((idx, b) in behaviors.withIndex()) {
            supabase.postgrest
                .from("behaviors")
                .update(BehaviorUpdate(name = b.name, sortOrder = idx)) {
                    filter { eq("id", b.id) }
                }
        }
    }

    // ---- Items -----------------------------------------------------------

    suspend fun fetchItems(planId: String): List<TrainingPlanItem> =
        supabase.postgrest
            .from("training_plan_items")
            .select {
                filter { eq("plan_id", planId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun fetchItemsForBehavior(behaviorId: String): List<TrainingPlanItem> =
        supabase.postgrest
            .from("training_plan_items")
            .select {
                filter { eq("behavior_id", behaviorId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun createItem(
        planId: String,
        behaviorId: String?,
        title: String,
        distance: Distance,
        duration: TrainingDuration,
        distraction: Distraction,
        distanceCustom: String?,
        durationCustom: String?,
        distractionCustom: String?,
        sortOrder: Int,
    ): TrainingPlanItem {
        val payload = ItemInsert(
            planId = planId,
            behaviorId = behaviorId,
            sortOrder = sortOrder,
            title = title,
            distance = distance,
            duration = duration,
            distraction = distraction,
            distanceCustom = distanceCustom,
            durationCustom = durationCustom,
            distractionCustom = distractionCustom,
        )
        return supabase.postgrest
            .from("training_plan_items")
            .insert(payload) { select() }
            .decodeSingle()
    }

    suspend fun updateItem(item: TrainingPlanItem): TrainingPlanItem {
        val payload = ItemUpdate(
            behaviorId = item.behaviorId,
            sortOrder = item.sortOrder,
            title = item.title,
            distance = item.distance,
            duration = item.duration,
            distraction = item.distraction,
            distanceCustom = item.distanceCustom,
            durationCustom = item.durationCustom,
            distractionCustom = item.distractionCustom,
        )
        return supabase.postgrest
            .from("training_plan_items")
            .update(payload) {
                select()
                filter { eq("id", item.id) }
            }
            .decodeSingle()
    }

    suspend fun deleteItem(id: String) {
        supabase.postgrest
            .from("training_plan_items")
            .delete { filter { eq("id", id) } }
    }

    /**
     * Persists new sort_order values for items in one behavior after a
     * drag-reorder. Per-row UPDATE — keeps IDs stable so any
     * `plan_assignments.current_item_id` references remain valid.
     *
     * Two-pass: see [reorderBehaviors] — UNIQUE(behavior_id, sort_order)
     * blocks single-pass swaps.
     */
    suspend fun reorderItems(items: List<TrainingPlanItem>) {
        for ((idx, item) in items.withIndex()) {
            supabase.postgrest
                .from("training_plan_items")
                .update(ItemSortOrderUpdate(sortOrder = SORT_STASH_BASE + idx)) {
                    filter { eq("id", item.id) }
                }
        }
        for ((idx, item) in items.withIndex()) {
            supabase.postgrest
                .from("training_plan_items")
                .update(ItemSortOrderUpdate(sortOrder = idx)) {
                    filter { eq("id", item.id) }
                }
        }
    }

    // ---- Assignments (trainer) -------------------------------------------

    suspend fun assignPlan(planId: String, guardianId: String, petId: String?): PlanAssignment {
        val trainerId = supabase.auth.currentUserOrNull()?.id
            ?: throw PlanException.NotAuthenticated
        val payload = AssignmentInsert(
            planId = planId,
            trainerId = trainerId,
            guardianId = guardianId,
            petId = petId,
            isShared = true,
        )
        return supabase.postgrest
            .from("plan_assignments")
            .insert(payload) { select() }
            .decodeSingle()
    }

    suspend fun fetchAssignments(planId: String): List<PlanAssignment> =
        supabase.postgrest
            .from("plan_assignments")
            .select {
                filter { eq("plan_id", planId) }
                order("assigned_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun deleteAssignment(id: String) {
        supabase.postgrest
            .from("plan_assignments")
            .delete { filter { eq("id", id) } }
    }

    // ---- Assignments (guardian) ------------------------------------------

    /** Returns the guardian's assigned plans joined with the plan rows. */
    suspend fun fetchAssignedPlans(): List<AssignedPlan> {
        val guardianId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
        val assignments: List<PlanAssignment> = supabase.postgrest
            .from("plan_assignments")
            .select {
                filter { eq("guardian_id", guardianId) }
                order("assigned_at", Order.DESCENDING)
            }
            .decodeList()
        if (assignments.isEmpty()) return emptyList()
        val planIds = assignments.map { it.planId }.distinct()
        val plans: List<TrainingPlan> = supabase.postgrest
            .from("training_plans")
            .select {
                filter { isIn("id", planIds) }
            }
            .decodeList()
        return assignments.mapNotNull { a ->
            val plan = plans.firstOrNull { it.id == a.planId } ?: return@mapNotNull null
            AssignedPlan(assignment = a, plan = plan)
        }
    }

    /** Items for a plan from the guardian's perspective (RLS-gated). */
    suspend fun fetchAssignedPlanItems(planId: String): List<TrainingPlanItem> = fetchItems(planId)

    suspend fun updateCurrentItem(assignmentId: String, itemId: String) {
        supabase.postgrest
            .from("plan_assignments")
            .update(CurrentItemUpdate(currentItemId = itemId)) {
                filter { eq("id", assignmentId) }
            }
    }

    /**
     * Two-fetch helper for record-detail: item → behavior → name.
     * Returns null for standalone sessions or items with no behavior.
     */
    suspend fun fetchBehaviorName(planItemId: String): String? {
        val item: TrainingPlanItem = supabase.postgrest
            .from("training_plan_items")
            .select { filter { eq("id", planItemId) } }
            .decodeSingleOrNull() ?: return null
        val behaviorId = item.behaviorId ?: return null
        val behavior: Behavior? = supabase.postgrest
            .from("behaviors")
            .select { filter { eq("id", behaviorId) } }
            .decodeSingleOrNull()
        return behavior?.name
    }
}

/** Join result — a guardian's assignment + the underlying plan. Not a DB row. */
data class AssignedPlan(
    val assignment: PlanAssignment,
    val plan: TrainingPlan,
) {
    val id: String get() = assignment.id
}

/** High enough to be outside any realistic real sort_order, low enough to avoid overflow concerns. */
private const val SORT_STASH_BASE: Int = 100_000

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

@Serializable
private data class BehaviorInsert(
    @SerialName("plan_id") val planId: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
private data class BehaviorUpdate(
    val name: String,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
private data class ItemInsert(
    @SerialName("plan_id") val planId: String,
    @SerialName("behavior_id") val behaviorId: String?,
    @SerialName("sort_order") val sortOrder: Int,
    val title: String,
    val distance: Distance,
    val duration: TrainingDuration,
    val distraction: Distraction,
    @SerialName("distance_custom") val distanceCustom: String?,
    @SerialName("duration_custom") val durationCustom: String?,
    @SerialName("distraction_custom") val distractionCustom: String?,
)

@Serializable
private data class ItemUpdate(
    @SerialName("behavior_id") val behaviorId: String?,
    @SerialName("sort_order") val sortOrder: Int,
    val title: String,
    val distance: Distance,
    val duration: TrainingDuration,
    val distraction: Distraction,
    @SerialName("distance_custom") val distanceCustom: String?,
    @SerialName("duration_custom") val durationCustom: String?,
    @SerialName("distraction_custom") val distractionCustom: String?,
)

@Serializable
private data class ItemSortOrderUpdate(
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
private data class AssignmentInsert(
    @SerialName("plan_id") val planId: String,
    @SerialName("trainer_id") val trainerId: String,
    @SerialName("guardian_id") val guardianId: String,
    @SerialName("pet_id") val petId: String?,
    @SerialName("is_shared") val isShared: Boolean,
)

@Serializable
private data class CurrentItemUpdate(
    @SerialName("current_item_id") val currentItemId: String,
)
