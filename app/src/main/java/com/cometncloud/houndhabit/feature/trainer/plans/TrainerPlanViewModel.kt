package com.cometncloud.houndhabit.feature.trainer.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.models.TrainingPlan
import com.cometncloud.houndhabit.core.services.TrainingPlanService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainerPlanUiState(
    val plans: List<TrainingPlan> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Phase 9a: trainer plan list/CRUD. Will grow in 9b (behaviors/items) and
 * 9c (assignments + progress) — keeping the surface minimal until then.
 */
class TrainerPlanViewModel(
    private val service: TrainingPlanService = TrainingPlanService(),
) : ViewModel() {

    private val _state = MutableStateFlow(TrainerPlanUiState())
    val state: StateFlow<TrainerPlanUiState> = _state.asStateFlow()

    fun clearError() = _state.update { it.copy(errorMessage = null) }

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
}
