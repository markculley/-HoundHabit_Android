package com.cometncloud.houndhabit.feature.guardian.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.services.AuthService
import com.cometncloud.houndhabit.core.services.InviteService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EnterCodeUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Set to a non-null trainer name once the link succeeds. */
    val linkedTrainerName: String? = null,
)

class EnterInviteCodeViewModel(
    private val inviteService: InviteService = InviteService(),
    private val authService: AuthService = AuthService(),
) : ViewModel() {

    private val _state = MutableStateFlow(EnterCodeUiState())
    val state: StateFlow<EnterCodeUiState> = _state.asStateFlow()

    fun setCode(value: String) {
        // Match iOS: uppercase, max 8 chars.
        val normalized = value.uppercase().take(8)
        _state.update { it.copy(code = normalized, errorMessage = null) }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    fun redeem() {
        val code = _state.value.code
        if (code.length < 8) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val invite = inviteService.fetchInviteByCode(code)
                inviteService.acceptInvite(invite)
                // Best-effort name lookup — failure shouldn't block the success state.
                val trainerName = runCatching {
                    authService.fetchProfile(invite.trainerId)?.fullName
                }.getOrNull()
                _state.update {
                    it.copy(linkedTrainerName = trainerName ?: "Your trainer")
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not redeem code.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
