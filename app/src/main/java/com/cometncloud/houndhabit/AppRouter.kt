package com.cometncloud.houndhabit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cometncloud.houndhabit.feature.auth.AuthGraph
import com.cometncloud.houndhabit.feature.guardian.GuardianScaffold
import com.cometncloud.houndhabit.feature.trainer.TrainerScaffold
import com.cometncloud.houndhabit.shared.error.ErrorStore

@Composable
fun AppRouter(viewModel: MainViewModel = viewModel()) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Drain the process-wide error channel and surface each message above
    // whatever screen is current. Per-screen snackbars still run on top of
    // their own SnackbarHosts — this one only catches errors raised from
    // outside any screen (services, workers, lifecycle hooks).
    LaunchedEffect(Unit) {
        ErrorStore.messages.collect { message ->
            snackbar.showSnackbar(message)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (route) {
                AppRoute.Loading -> CenteredProgress()
                AppRoute.Auth -> AuthGraph()
                AppRoute.Guardian -> GuardianScaffold(onSignOut = viewModel::signOut)
                AppRoute.Trainer -> TrainerScaffold(onSignOut = viewModel::signOut)
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
