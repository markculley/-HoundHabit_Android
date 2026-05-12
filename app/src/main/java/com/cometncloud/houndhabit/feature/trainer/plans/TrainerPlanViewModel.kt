package com.cometncloud.houndhabit.feature.trainer.plans

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.models.Behavior
import com.cometncloud.houndhabit.core.models.Distance
import com.cometncloud.houndhabit.core.models.Distraction
import com.cometncloud.houndhabit.core.models.LinkedGuardian
import com.cometncloud.houndhabit.core.models.Pet
import com.cometncloud.houndhabit.core.models.PlanAssignment
import com.cometncloud.houndhabit.core.models.TrainingDuration
import com.cometncloud.houndhabit.core.models.TrainingPlan
import com.cometncloud.houndhabit.core.models.TrainingPlanItem
import com.cometncloud.houndhabit.core.services.InviteService
import com.cometncloud.houndhabit.core.services.PetService
import com.cometncloud.houndhabit.core.services.TrainingPlanService
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "TrainerPlanVM"

/**
 * Returns a short, human-friendly error message and logs the full exception.
 * Supabase's [RestException] message is enormous (full request dump); strip
 * it down to status + Postgres error so the snackbar is readable.
 */
private fun summarize(t: Throwable, fallback: String): String {
    Log.e(TAG, fallback, t)
    return when (t) {
        is RestException -> "${fallback} (${t.statusCode}: ${t.error})"
        else -> t.message?.take(160) ?: fallback
    }
}

enum class PlanProgress { ToDo, InProgress, Done }

val PlanProgress.label: String
    get() = when (this) {
        PlanProgress.ToDo -> "To Do"
        PlanProgress.InProgress -> "In Progress"
        PlanProgress.Done -> "Done"
    }

data class TrainerPlanUiState(
    val plans: List<TrainingPlan> = emptyList(),
    /** Behaviors keyed by plan id. */
    val behaviors: Map<String, List<Behavior>> = emptyMap(),
    /** Items keyed by plan id (a flat list across that plan's behaviors). */
    val items: Map<String, List<TrainingPlanItem>> = emptyMap(),
    /** Assignments keyed by plan id. */
    val assignments: Map<String, List<PlanAssignment>> = emptyMap(),
    /** Trainer's currently linked guardians, used by the assign sheet. */
    val linkedGuardians: List<LinkedGuardian> = emptyList(),
    /** Pet cache keyed by pet id; populated lazily as assignments load. */
    val pets: Map<String, Pet> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class TrainerPlanViewModel(
    private val service: TrainingPlanService = TrainingPlanService(),
    private val inviteService: InviteService = InviteService(),
    private val petService: PetService = PetService(),
) : ViewModel() {

    private val _state = MutableStateFlow(TrainerPlanUiState())
    val state: StateFlow<TrainerPlanUiState> = _state.asStateFlow()

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    // ---- Plans -----------------------------------------------------------

    fun loadPlans() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                _state.update { it.copy(plans = service.fetchPlans()) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load plans.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun createPlan(title: String, description: String?) {
        viewModelScope.launch {
            try {
                val plan = service.createPlan(title, description)
                _state.update { it.copy(plans = listOf(plan) + it.plans) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not create plan.") }
            }
        }
    }

    fun updatePlan(plan: TrainingPlan) {
        viewModelScope.launch {
            try {
                val saved = service.updatePlan(plan)
                _state.update { s ->
                    s.copy(plans = s.plans.map { if (it.id == saved.id) saved else it })
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not update plan.") }
            }
        }
    }

    /**
     * Inject a plan into the VM's state without a network call. Used by
     * `OwnedPlanDetailScreen` so the guardian-owned-plan host shares the
     * same screen as the trainer detail.
     */
    fun adoptPlan(plan: TrainingPlan) {
        _state.update { s ->
            if (s.plans.any { it.id == plan.id }) s
            else s.copy(plans = listOf(plan) + s.plans)
        }
    }

    fun deletePlan(plan: TrainingPlan) {
        viewModelScope.launch {
            try {
                service.deletePlan(plan.id)
                _state.update { s -> s.copy(plans = s.plans.filterNot { it.id == plan.id }) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not delete plan.") }
            }
        }
    }

    // ---- Behaviors -------------------------------------------------------

    fun loadBehaviors(planId: String) {
        viewModelScope.launch {
            try {
                val list = service.fetchBehaviors(planId)
                _state.update { it.copy(behaviors = it.behaviors + (planId to list)) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load behaviors.") }
            }
        }
    }

    fun addBehavior(planId: String, name: String) {
        viewModelScope.launch {
            try {
                val nextOrder = _state.value.behaviors[planId].orEmpty().size
                val behavior = service.createBehavior(planId, name, nextOrder)
                _state.update {
                    val current = it.behaviors[planId].orEmpty()
                    it.copy(behaviors = it.behaviors + (planId to current + behavior))
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not add behavior.") }
            }
        }
    }

    fun updateBehavior(behavior: Behavior) {
        viewModelScope.launch {
            try {
                val saved = service.updateBehavior(behavior)
                _state.update { s ->
                    val current = s.behaviors[behavior.planId].orEmpty()
                    s.copy(
                        behaviors = s.behaviors + (behavior.planId to current.map {
                            if (it.id == saved.id) saved else it
                        }),
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not update behavior.") }
            }
        }
    }

    fun deleteBehavior(behavior: Behavior) {
        viewModelScope.launch {
            try {
                service.deleteBehavior(behavior.id)
                _state.update { s ->
                    val behaviors = s.behaviors[behavior.planId].orEmpty().filterNot { it.id == behavior.id }
                    val items = s.items[behavior.planId].orEmpty().filterNot { it.behaviorId == behavior.id }
                    s.copy(
                        behaviors = s.behaviors + (behavior.planId to behaviors),
                        items = s.items + (behavior.planId to items),
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not delete behavior.") }
            }
        }
    }

    /** Move behavior at [fromIndex] to [toIndex] within its plan. */
    fun moveBehavior(planId: String, fromIndex: Int, toIndex: Int) {
        val current = _state.value.behaviors[planId].orEmpty().toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        // Optimistic in-memory update with rewritten sortOrders.
        val rewritten = current.mapIndexed { idx, b -> b.copy(sortOrder = idx) }
        _state.update { it.copy(behaviors = it.behaviors + (planId to rewritten)) }
        viewModelScope.launch {
            try {
                service.reorderBehaviors(rewritten)
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not reorder behaviors.")) }
                loadBehaviors(planId)
            }
        }
    }

    // ---- Items -----------------------------------------------------------

    fun loadItems(planId: String) {
        viewModelScope.launch {
            try {
                val list = service.fetchItems(planId)
                _state.update { it.copy(items = it.items + (planId to list)) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load steps.") }
            }
        }
    }

    fun addItem(
        planId: String,
        behaviorId: String?,
        title: String,
        distance: Distance,
        duration: TrainingDuration,
        distraction: Distraction,
        distanceCustom: String?,
        durationCustom: String?,
        distractionCustom: String?,
    ) {
        viewModelScope.launch {
            try {
                val behaviorItems = _state.value.items[planId].orEmpty()
                    .filter { it.behaviorId == behaviorId }
                val nextOrder = behaviorItems.size
                val item = service.createItem(
                    planId = planId,
                    behaviorId = behaviorId,
                    title = title,
                    distance = distance,
                    duration = duration,
                    distraction = distraction,
                    distanceCustom = distanceCustom,
                    durationCustom = durationCustom,
                    distractionCustom = distractionCustom,
                    sortOrder = nextOrder,
                )
                _state.update {
                    val current = it.items[planId].orEmpty()
                    it.copy(items = it.items + (planId to current + item))
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not add step.") }
            }
        }
    }

    fun updateItem(item: TrainingPlanItem) {
        viewModelScope.launch {
            try {
                val saved = service.updateItem(item)
                _state.update { s ->
                    val current = s.items[item.planId].orEmpty()
                    s.copy(
                        items = s.items + (item.planId to current.map {
                            if (it.id == saved.id) saved else it
                        }),
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not update step.") }
            }
        }
    }

    fun deleteItem(item: TrainingPlanItem) {
        viewModelScope.launch {
            try {
                service.deleteItem(item.id)
                _state.update { s ->
                    val current = s.items[item.planId].orEmpty().filterNot { it.id == item.id }
                    s.copy(items = s.items + (item.planId to current))
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not delete step.") }
            }
        }
    }

    /** Move item within its behavior. [fromIndex]/[toIndex] are scoped to that behavior's items. */
    fun moveItem(planId: String, behaviorId: String?, fromIndex: Int, toIndex: Int) {
        val all = _state.value.items[planId].orEmpty()
        val behaviorItems = all.filter { it.behaviorId == behaviorId }.toMutableList()
        if (fromIndex !in behaviorItems.indices || toIndex !in behaviorItems.indices || fromIndex == toIndex) return
        val moved = behaviorItems.removeAt(fromIndex)
        behaviorItems.add(toIndex, moved)
        val rewritten = behaviorItems.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        // Splice rewritten back into the plan-level list (other behaviors untouched).
        val merged = all.filterNot { it.behaviorId == behaviorId } + rewritten
        _state.update { it.copy(items = it.items + (planId to merged)) }
        viewModelScope.launch {
            try {
                service.reorderItems(rewritten)
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not reorder steps.")) }
                loadItems(planId)
            }
        }
    }

    // ---- Assignments -----------------------------------------------------

    fun loadAssignments(planId: String) {
        viewModelScope.launch {
            try {
                val assignments = service.fetchAssignments(planId)
                _state.update { it.copy(assignments = it.assignments + (planId to assignments)) }
                // Load any pets we don't yet have cached for the assigned guardians.
                val knownPetIds = _state.value.pets.keys
                val missingPetIds = assignments.mapNotNull { it.petId }.filterNot { it in knownPetIds }
                if (missingPetIds.isNotEmpty()) {
                    val guardianIds = assignments.map { it.guardianId }.distinct()
                    val fetchedPets = guardianIds.flatMap {
                        runCatching { petService.fetchPets(it) }.getOrDefault(emptyList())
                    }
                    if (fetchedPets.isNotEmpty()) {
                        _state.update { s ->
                            s.copy(pets = s.pets + fetchedPets.associateBy { it.id })
                        }
                    }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not load assignments.")) }
            }
        }
    }

    fun loadLinkedGuardians() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(linkedGuardians = inviteService.fetchLinkedGuardians()) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not load guardians.")) }
            }
        }
    }

    fun assignPlan(planId: String, guardianId: String, petId: String?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val assignment = service.assignPlan(planId, guardianId, petId)
                _state.update { s ->
                    val current = s.assignments[planId].orEmpty()
                    s.copy(assignments = s.assignments + (planId to listOf(assignment) + current))
                }
                onDone()
            } catch (t: Throwable) {
                val msg = t.message.orEmpty()
                val friendly = if (msg.contains("duplicate", true) || msg.contains("unique", true)) {
                    "This guardian is already assigned to this plan."
                } else {
                    summarize(t, "Could not assign plan.")
                }
                _state.update { it.copy(errorMessage = friendly) }
            }
        }
    }

    fun deleteAssignment(assignment: PlanAssignment) {
        viewModelScope.launch {
            try {
                service.deleteAssignment(assignment.id)
                _state.update { s ->
                    val current = s.assignments[assignment.planId].orEmpty()
                        .filterNot { it.id == assignment.id }
                    s.copy(assignments = s.assignments + (assignment.planId to current))
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = summarize(t, "Could not remove assignment.")) }
            }
        }
    }

    fun planProgress(assignment: PlanAssignment): PlanProgress {
        val currentId = assignment.currentItemId ?: return PlanProgress.ToDo
        val sorted = _state.value.items[assignment.planId].orEmpty().sortedBy { it.sortOrder }
        val last = sorted.lastOrNull() ?: return PlanProgress.InProgress
        return if (currentId == last.id) PlanProgress.Done else PlanProgress.InProgress
    }

    fun guardianName(guardianId: String): String =
        _state.value.linkedGuardians.firstOrNull { it.guardianId == guardianId }?.profile?.fullName
            ?: "Guardian"

    fun petName(petId: String?): String =
        if (petId == null) "Any pet" else _state.value.pets[petId]?.name ?: "Pet"

    // ---- Helpers ---------------------------------------------------------

    fun stepCount(planId: String, behaviorId: String): Int =
        _state.value.items[planId].orEmpty().count { it.behaviorId == behaviorId }
}
