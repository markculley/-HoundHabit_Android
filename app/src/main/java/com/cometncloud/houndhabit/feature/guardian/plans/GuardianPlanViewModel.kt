package com.cometncloud.houndhabit.feature.guardian.plans

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Behavior
import com.cometncloud.houndhabit.core.models.TrainingPlan
import com.cometncloud.houndhabit.core.models.TrainingPlanItem
import com.cometncloud.houndhabit.core.services.AssignedPlan
import com.cometncloud.houndhabit.core.services.TrainingPlanService
import com.cometncloud.houndhabit.feature.trainer.plans.PlanProgress
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "GuardianPlanVM"

private fun summarize(t: Throwable, fallback: String): String {
    Log.e(TAG, fallback, t)
    return when (t) {
        is RestException -> "$fallback (${t.statusCode}: ${t.error})"
        else -> t.message?.take(160) ?: fallback
    }
}

data class GuardianPlanUiState(
    val assignedPlans: List<AssignedPlan> = emptyList(),
    val items: Map<String, List<TrainingPlanItem>> = emptyMap(),
    val behaviors: Map<String, List<Behavior>> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastAdvancementMessage: String? = null,
)

class GuardianPlanViewModel(
    private val service: TrainingPlanService = TrainingPlanService(),
) : ViewModel() {

    private val _state = MutableStateFlow(GuardianPlanUiState())
    val state: StateFlow<GuardianPlanUiState> = _state.asStateFlow()

    fun clearError() = _state.update { it.copy(errorMessage = null) }
    fun clearAdvancementMessage() = _state.update { it.copy(lastAdvancementMessage = null) }

    val currentUserId: String? get() = SupabaseClient.client.auth.currentUserOrNull()?.id

    fun isOwnPlan(ap: AssignedPlan): Boolean =
        currentUserId?.let { ap.plan.trainerId == it } == true

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val plans = service.fetchAssignedPlans()
                _state.update { it.copy(assignedPlans = plans) }
                // Lazy: items/behaviors are fetched per-plan when the user opens detail.
                // Trainer-side progress preview not surfaced on the list (yet).
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not load plans.")) }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadPlanDetails(planId: String) {
        viewModelScope.launch {
            try {
                val (items, behaviors) = coroutineScope {
                    val i = async { service.fetchAssignedPlanItems(planId) }
                    val b = async { service.fetchBehaviors(planId) }
                    i.await() to b.await()
                }
                _state.update {
                    it.copy(
                        items = it.items + (planId to items),
                        behaviors = it.behaviors + (planId to behaviors),
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not load plan.")) }
            }
        }
    }

    fun planProgress(assignedPlan: AssignedPlan): PlanProgress {
        val currentId = assignedPlan.assignment.currentItemId ?: return PlanProgress.ToDo
        val sorted = _state.value.items[assignedPlan.plan.id].orEmpty().sortedBy { it.sortOrder }
        val last = sorted.lastOrNull() ?: return PlanProgress.InProgress
        return if (currentId == last.id) PlanProgress.Done else PlanProgress.InProgress
    }

    /** First step by sortOrder, or the step matching `currentItemId` if set. */
    fun currentItem(assignedPlan: AssignedPlan, items: List<TrainingPlanItem>): TrainingPlanItem? {
        val sorted = items.sortedBy { it.sortOrder }
        if (sorted.isEmpty()) return null
        val currentId = assignedPlan.assignment.currentItemId ?: return sorted.first()
        return sorted.firstOrNull { it.id == currentId } ?: sorted.first()
    }

    /**
     * Items ordered by behavior.sortOrder then item.sortOrder (item.sortOrder is
     * scoped per-behavior, so a flat sort wouldn't work). Unbound items (no
     * behavior) tail the list.
     */
    fun orderedItems(planId: String): List<TrainingPlanItem> {
        val behaviors = _state.value.behaviors[planId].orEmpty().sortedBy { it.sortOrder }
        val items = _state.value.items[planId].orEmpty()
        val result = mutableListOf<TrainingPlanItem>()
        for (b in behaviors) {
            result += items.filter { it.behaviorId == b.id }.sortedBy { it.sortOrder }
        }
        result += items.filter { it.behaviorId == null }.sortedBy { it.sortOrder }
        return result
    }

    /**
     * Computes the new current step from a 0–5 score, persists it, mirrors
     * the assignment in-memory, and stashes a [lastAdvancementMessage] for
     * the UI to surface.
     */
    fun advanceCurrentStep(assignedPlan: AssignedPlan, score: Int) {
        val sorted = orderedItems(assignedPlan.plan.id)
        if (sorted.isEmpty()) return
        val currentId = assignedPlan.assignment.currentItemId ?: sorted.first().id
        val currentIdx = sorted.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: return

        val newIdx = when {
            score == 5 -> minOf(currentIdx + 1, sorted.lastIndex)
            score <= 1 -> maxOf(currentIdx - 1, 0)
            else -> currentIdx
        }
        val newItem = sorted[newIdx]
        val message = advancementMessage(score, currentIdx, newIdx, newItem)
        _state.update { it.copy(lastAdvancementMessage = message) }

        if (newItem.id == assignedPlan.assignment.currentItemId) return

        viewModelScope.launch {
            try {
                service.updateCurrentItem(assignedPlan.assignment.id, newItem.id)
                _state.update { s ->
                    val updatedPlans = s.assignedPlans.map { ap ->
                        if (ap.assignment.id == assignedPlan.assignment.id) {
                            ap.copy(assignment = ap.assignment.copy(currentItemId = newItem.id))
                        } else ap
                    }
                    s.copy(assignedPlans = updatedPlans)
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not advance step.")) }
            }
        }
    }

    /**
     * After [TrainingPlanService.createPlan] persists the plan, register a
     * self-assignment so it appears in the guardian's plan list. Also seeds
     * empty items/behaviors so the detail screen doesn't load-spin forever.
     */
    fun adoptCreatedPlan(plan: TrainingPlan, petId: String?) {
        viewModelScope.launch {
            try {
                val assignment = service.selfAssignPlan(plan.id, petId)
                _state.update { s ->
                    s.copy(
                        assignedPlans = listOf(AssignedPlan(assignment, plan)) + s.assignedPlans,
                        items = s.items + (plan.id to emptyList()),
                        behaviors = s.behaviors + (plan.id to emptyList()),
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not register own plan.")) }
            }
        }
    }

    fun deleteOwnPlan(plan: TrainingPlan) {
        viewModelScope.launch {
            try {
                service.deletePlan(plan.id)
                _state.update { s ->
                    s.copy(
                        assignedPlans = s.assignedPlans.filterNot { it.plan.id == plan.id },
                        items = s.items - plan.id,
                        behaviors = s.behaviors - plan.id,
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not delete plan.")) }
            }
        }
    }

    fun updateSharing(ap: AssignedPlan, isShared: Boolean) {
        // Optimistic in-memory update, revert on failure.
        _state.update { s ->
            s.copy(assignedPlans = s.assignedPlans.map {
                if (it.assignment.id == ap.assignment.id)
                    it.copy(assignment = it.assignment.copy(isShared = isShared))
                else it
            })
        }
        viewModelScope.launch {
            try {
                service.updateAssignmentSharing(ap.assignment.id, isShared)
            } catch (t: Throwable) {
                _state.update { s ->
                    s.copy(
                        errorMessage = summarize(t, "Could not update sharing."),
                        assignedPlans = s.assignedPlans.map {
                            if (it.assignment.id == ap.assignment.id)
                                it.copy(assignment = it.assignment.copy(isShared = !isShared))
                            else it
                        },
                    )
                }
            }
        }
    }

    fun updateAssignmentPet(ap: AssignedPlan, petId: String?) {
        _state.update { s ->
            s.copy(assignedPlans = s.assignedPlans.map {
                if (it.assignment.id == ap.assignment.id)
                    it.copy(assignment = it.assignment.copy(petId = petId))
                else it
            })
        }
        viewModelScope.launch {
            try {
                service.updateAssignmentPet(ap.assignment.id, petId)
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not link pet.")) }
                load() // resync from server on failure
            }
        }
    }

    private fun advancementMessage(
        score: Int,
        oldIdx: Int,
        newIdx: Int,
        item: TrainingPlanItem,
    ): String {
        val n = item.sortOrder + 1
        return when {
            score == 5 && newIdx > oldIdx -> "Great work! Moving to step $n: ${item.title}."
            score == 5 -> "Amazing — you've mastered all the steps in this plan!"
            score <= 1 && newIdx < oldIdx -> "Let's build some confidence on step $n: ${item.title}."
            score <= 1 -> "Keep working on step $n. You've got this!"
            else -> "Good progress! Keep practicing step $n: ${item.title}."
        }
    }
}
