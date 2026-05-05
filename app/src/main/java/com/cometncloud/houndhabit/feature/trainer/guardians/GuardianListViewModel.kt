package com.cometncloud.houndhabit.feature.trainer.guardians

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.models.LinkedGuardian
import com.cometncloud.houndhabit.core.services.InviteService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuardianListUiState(
    val guardians: List<LinkedGuardian> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class GuardianListViewModel(
    private val service: InviteService = InviteService(),
) : ViewModel() {

    private val _state = MutableStateFlow(GuardianListUiState())
    val state: StateFlow<GuardianListUiState> = _state.asStateFlow()

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                _state.update { it.copy(guardians = service.fetchLinkedGuardians()) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load guardians.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
