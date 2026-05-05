package com.cometncloud.houndhabit.feature.trainer.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.models.Invite
import com.cometncloud.houndhabit.core.services.InviteService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InviteUiState(
    val invites: List<Invite> = emptyList(),
    val email: String = "",
    val sentCode: String? = null,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

class InviteViewModel(
    private val service: InviteService = InviteService(),
) : ViewModel() {

    private val _state = MutableStateFlow(InviteUiState())
    val state: StateFlow<InviteUiState> = _state.asStateFlow()

    fun setEmail(value: String) = _state.update { it.copy(email = value, errorMessage = null) }
    fun clearError() = _state.update { it.copy(errorMessage = null) }
    fun dismissSentCode() = _state.update { it.copy(sentCode = null) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                _state.update { it.copy(invites = service.fetchInvites()) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not load invites.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun send() {
        val trimmed = _state.value.email.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, errorMessage = null) }
            try {
                val invite = service.createInvite(trimmed)
                _state.update {
                    it.copy(
                        invites = listOf(invite) + it.invites,
                        sentCode = invite.code,
                        email = "",
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message ?: "Could not send invite.") }
            } finally {
                _state.update { it.copy(isSending = false) }
            }
        }
    }
}
