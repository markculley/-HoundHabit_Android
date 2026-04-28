package com.cometncloud.houndhabit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cometncloud.houndhabit.core.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

/**
 * Single source of truth for top-level navigation. Observes Supabase session status and switches
 * between the auth, guardian, and trainer graphs.
 *
 * Phase 1: only the unauthenticated branch is rendered as a placeholder. Phase 2 will replace it
 * with the auth nav graph; Phase 3+ will fill in the guardian/trainer graphs.
 */
@Composable
fun AppRouter() {
    val sessionStatus by SupabaseClient.client.auth.sessionStatus.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        when (sessionStatus) {
            is SessionStatus.Initializing -> CenteredProgress()
            is SessionStatus.NotAuthenticated -> Placeholder("Auth — not implemented yet (Phase 2)")
            is SessionStatus.RefreshFailure -> Placeholder("Auth — session refresh failed")
            is SessionStatus.Authenticated -> Placeholder("Authenticated — role routing arrives in Phase 2")
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}
