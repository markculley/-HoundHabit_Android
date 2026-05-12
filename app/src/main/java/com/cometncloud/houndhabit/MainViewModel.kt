package com.cometncloud.houndhabit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Role
import com.cometncloud.houndhabit.core.services.AuthService
import com.cometncloud.houndhabit.shared.error.ErrorStore
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppRoute {
    data object Loading : AppRoute
    data object Auth : AppRoute
    data object Guardian : AppRoute
    data object Trainer : AppRoute
}

class MainViewModel(
    private val authService: AuthService = AuthService(),
) : ViewModel() {

    private val supabase get() = SupabaseClient.client

    val route: StateFlow<AppRoute> = supabase.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Initializing -> AppRoute.Loading
                is SessionStatus.NotAuthenticated -> AppRoute.Auth
                is SessionStatus.RefreshFailure -> AppRoute.Auth
                is SessionStatus.Authenticated -> resolveAuthenticatedRoute()
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppRoute.Loading,
        )

    fun signOut() {
        viewModelScope.launch {
            runCatching { authService.signOut() }
        }
    }

    private suspend fun resolveAuthenticatedRoute(): AppRoute {
        val result = runCatching { authService.currentProfile() }
        result.exceptionOrNull()?.let { ErrorStore.emit(it, "Could not load your profile.") }
        return when (result.getOrNull()?.role) {
            Role.Guardian -> AppRoute.Guardian
            Role.Trainer -> AppRoute.Trainer
            null -> AppRoute.Loading // profile trigger may not have run yet; sessionStatus will re-emit
        }
    }
}
