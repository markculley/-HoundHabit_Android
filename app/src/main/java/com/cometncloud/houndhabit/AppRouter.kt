package com.cometncloud.houndhabit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cometncloud.houndhabit.feature.auth.AuthGraph
import com.cometncloud.houndhabit.feature.guardian.GuardianScaffold
import com.cometncloud.houndhabit.feature.trainer.TrainerScaffold

@Composable
fun AppRouter(viewModel: MainViewModel = viewModel()) {
    val route by viewModel.route.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        when (route) {
            AppRoute.Loading -> CenteredProgress()
            AppRoute.Auth -> AuthGraph()
            AppRoute.Guardian -> GuardianScaffold(onSignOut = viewModel::signOut)
            AppRoute.Trainer -> TrainerScaffold(onSignOut = viewModel::signOut)
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
