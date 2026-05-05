package com.cometncloud.houndhabit.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cometncloud.houndhabit.feature.guardian.settings.SettingsScreen
import com.cometncloud.houndhabit.feature.trainer.guardians.GuardianListScreen
import com.cometncloud.houndhabit.feature.trainer.guardians.GuardianListViewModel
import com.cometncloud.houndhabit.feature.trainer.invite.InviteScreen
import com.cometncloud.houndhabit.feature.trainer.invite.InviteViewModel

private object Routes {
    const val GUARDIANS = "guardians"
    const val PLANS = "plans"
    const val INVITE = "invite"
    const val SETTINGS = "settings"
}

private data class TrainerTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TrainerTab(Routes.GUARDIANS, "Guardians", Icons.Filled.PeopleAlt),
    TrainerTab(Routes.PLANS, "Plans", Icons.AutoMirrored.Filled.ListAlt),
    TrainerTab(Routes.INVITE, "Invite", Icons.Filled.Email),
    TrainerTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun TrainerScaffold(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val guardianListViewModel: GuardianListViewModel = viewModel()
    val inviteViewModel: InviteViewModel = viewModel()

    Scaffold(bottomBar = { BottomBar(navController) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.GUARDIANS,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Routes.GUARDIANS) {
                GuardianListScreen(
                    viewModel = guardianListViewModel,
                    onGuardianClick = { /* Phase 8 — guardian detail screen */ },
                )
            }
            composable(Routes.PLANS) {
                ComingSoon("Plans", "Trainer plan authoring arrives in Phase 9.")
            }
            composable(Routes.INVITE) {
                InviteScreen(viewModel = inviteViewModel)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    isTrainer = true,
                    onSignOut = onSignOut,
                    onEnterInviteCode = {},
                )
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}

@Composable
private fun ComingSoon(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
