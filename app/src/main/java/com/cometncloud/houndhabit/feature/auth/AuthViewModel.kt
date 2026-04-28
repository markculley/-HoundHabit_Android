package com.cometncloud.houndhabit.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.models.Role
import com.cometncloud.houndhabit.core.services.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val selectedRole: Role = Role.Guardian,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class AuthViewModel(
    private val authService: AuthService = AuthService(),
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setEmail(value: String) = _state.update { it.copy(email = value, errorMessage = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, errorMessage = null) }
    fun setFullName(value: String) = _state.update { it.copy(fullName = value, errorMessage = null) }
    fun setRole(role: Role) = _state.update { it.copy(selectedRole = role) }

    fun signIn() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                authService.signIn(email = s.email.trim(), password = s.password)
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = friendlyMessage(t)) }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signUp() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                authService.signUp(
                    email = s.email.trim(),
                    password = s.password,
                    fullName = s.fullName.trim(),
                    role = s.selectedRole,
                )
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = friendlyMessage(t)) }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun friendlyMessage(t: Throwable): String {
        val raw = (t.message ?: "").lowercase()
        return when {
            "email" in raw && ("invalid" in raw || "phone" in raw) ->
                "Please enter a valid email address."
            "invalid login credentials" in raw || "invalid password" in raw ->
                "Incorrect email or password."
            "user already registered" in raw || "already been registered" in raw ->
                "An account with this email already exists."
            "password should be at least" in raw ->
                "Password must be at least 6 characters."
            "network" in raw || "internet" in raw || "unable to resolve host" in raw ->
                "Connection error. Please check your internet and try again."
            else -> t.message ?: "Something went wrong."
        }
    }
}
