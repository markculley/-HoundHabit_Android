package com.cometncloud.houndhabit.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class AuthScreen { Login, SignUp, RoleSelection }

@Composable
fun AuthGraph() {
    val viewModel: AuthViewModel = viewModel()
    var screen by rememberSaveable { mutableStateOf(AuthScreen.Login) }

    when (screen) {
        AuthScreen.Login -> LoginScreen(
            viewModel = viewModel,
            onSignUpClick = { screen = AuthScreen.SignUp },
        )
        AuthScreen.SignUp -> SignUpScreen(
            viewModel = viewModel,
            onContinue = { screen = AuthScreen.RoleSelection },
            onBack = { screen = AuthScreen.Login },
        )
        AuthScreen.RoleSelection -> RoleSelectionScreen(
            viewModel = viewModel,
            onBack = { screen = AuthScreen.SignUp },
        )
    }
}
