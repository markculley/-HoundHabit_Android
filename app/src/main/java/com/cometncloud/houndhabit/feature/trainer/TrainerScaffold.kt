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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.feature.guardian.records.TrainerRecordDetailScreen
import com.cometncloud.houndhabit.feature.guardian.settings.SettingsScreen
import com.cometncloud.houndhabit.feature.trainer.guardians.GuardianDetailScreen
import com.cometncloud.houndhabit.feature.trainer.guardians.GuardianDetailViewModel
import com.cometncloud.houndhabit.feature.trainer.guardians.GuardianListScreen
import com.cometncloud.houndhabit.feature.trainer.guardians.GuardianListViewModel
import com.cometncloud.houndhabit.feature.trainer.invite.InviteScreen
import com.cometncloud.houndhabit.feature.trainer.invite.InviteViewModel
import io.github.jan.supabase.auth.auth

private object Routes {
    const val GUARDIANS = "guardians"
    const val GUARDIAN_DETAIL = "guardians/detail"
    const val GUARDIAN_RECORD_DETAIL = "guardians/record"
    const val PLANS = "plans"
    const val INVITE = "invite"
    const val SETTINGS = "settings"

    fun guardianDetail(linkId: String) = "$GUARDIAN_DETAIL/$linkId"
    fun guardianRecordDetail(recordId: String) = "$GUARDIAN_RECORD_DETAIL/$recordId"
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
    // Hoisted so the guardian-detail screen and the trainer record-detail screen
    // share one VM instance — the record-detail entry needs the comments map
    // and pets cache that detail loaded.
    val guardianDetailViewModel: GuardianDetailViewModel = viewModel()

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
                    onGuardianClick = { linked ->
                        navController.navigate(Routes.guardianDetail(linked.id))
                    },
                )
            }
            composable("${Routes.GUARDIAN_DETAIL}/{linkId}") { backStack ->
                val linkId = backStack.arguments?.getString("linkId").orEmpty()
                val listState by guardianListViewModel.state.collectAsStateWithLifecycle()
                val guardian = listState.guardians.firstOrNull { it.id == linkId }
                if (guardian == null) {
                    // Stale nav entry — list reload will repopulate. Pop back.
                    androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    GuardianDetailScreen(
                        guardian = guardian,
                        viewModel = guardianDetailViewModel,
                        onBack = { navController.popBackStack() },
                        onRecordClick = { record ->
                            navController.navigate(Routes.guardianRecordDetail(record.id))
                        },
                    )
                }
            }
            composable("${Routes.GUARDIAN_RECORD_DETAIL}/{recordId}") { backStack ->
                val recordId = backStack.arguments?.getString("recordId").orEmpty()
                val detailState by guardianDetailViewModel.state.collectAsStateWithLifecycle()
                val record = detailState.records.firstOrNull { it.id == recordId }
                if (record == null) {
                    androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val petName = guardianDetailViewModel.petName(record.petId)
                    val comments = detailState.comments[record.id].orEmpty()
                    val currentUserId = androidx.compose.runtime.remember {
                        SupabaseClient.client.auth.currentUserOrNull()?.id
                    }
                    TrainerRecordDetailScreen(
                        record = record,
                        petName = petName,
                        comments = comments,
                        currentUserId = currentUserId,
                        onLoadComments = { guardianDetailViewModel.loadComments(record.id) },
                        onAddComment = { body ->
                            guardianDetailViewModel.addComment(record.id, body)
                        },
                        onDeleteComment = { c ->
                            guardianDetailViewModel.deleteComment(c)
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
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
            val selected = currentRoute == tab.route ||
                (tab.route == Routes.GUARDIANS && (
                    currentRoute?.startsWith(Routes.GUARDIAN_DETAIL) == true ||
                        currentRoute?.startsWith(Routes.GUARDIAN_RECORD_DETAIL) == true
                ))
            NavigationBarItem(
                selected = selected,
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
