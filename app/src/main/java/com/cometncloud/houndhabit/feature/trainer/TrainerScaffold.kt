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
import androidx.navigation.compose.navigation
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
import com.cometncloud.houndhabit.feature.trainer.plans.BehaviorDetailScreen
import com.cometncloud.houndhabit.feature.trainer.plans.PlanDetailScreen
import com.cometncloud.houndhabit.feature.trainer.plans.PlanListScreen
import com.cometncloud.houndhabit.feature.trainer.plans.TrainerPlanViewModel
import io.github.jan.supabase.auth.auth

private object Routes {
    // Graph routes (one per tab)
    const val GUARDIANS_GRAPH = "guardians_graph"
    const val PLANS_GRAPH = "plans_graph"
    const val INVITE_GRAPH = "invite_graph"
    const val SETTINGS_GRAPH = "settings_graph"

    // Guardians subgraph
    const val GUARDIANS = "guardians"
    const val GUARDIAN_DETAIL = "guardians/detail/{linkId}"
    const val GUARDIAN_RECORD_DETAIL = "guardians/record/{recordId}"

    // Plans subgraph
    const val PLANS = "plans"
    const val PLAN_DETAIL = "plans/detail/{planId}"
    const val BEHAVIOR_DETAIL = "plans/behavior/{planId}/{behaviorId}"

    // Invite subgraph
    const val INVITE = "invite"

    // Settings subgraph
    const val SETTINGS = "settings"

    fun guardianDetail(linkId: String) = "guardians/detail/$linkId"
    fun guardianRecordDetail(recordId: String) = "guardians/record/$recordId"
    fun planDetail(planId: String) = "plans/detail/$planId"
    fun behaviorDetail(planId: String, behaviorId: String) =
        "plans/behavior/$planId/$behaviorId"
}

private data class TrainerTab(val graphRoute: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TrainerTab(Routes.GUARDIANS_GRAPH, "Guardians", Icons.Filled.PeopleAlt),
    TrainerTab(Routes.PLANS_GRAPH, "Plans", Icons.AutoMirrored.Filled.ListAlt),
    TrainerTab(Routes.INVITE_GRAPH, "Invite", Icons.Filled.Email),
    TrainerTab(Routes.SETTINGS_GRAPH, "Settings", Icons.Filled.Settings),
)

@Composable
fun TrainerScaffold(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val guardianListViewModel: GuardianListViewModel = viewModel()
    val inviteViewModel: InviteViewModel = viewModel()
    val guardianDetailViewModel: GuardianDetailViewModel = viewModel()
    val trainerPlanViewModel: TrainerPlanViewModel = viewModel()

    Scaffold(bottomBar = { BottomBar(navController) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.GUARDIANS_GRAPH,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ---- Guardians ------------------------------------------------
            navigation(startDestination = Routes.GUARDIANS, route = Routes.GUARDIANS_GRAPH) {
                composable(Routes.GUARDIANS) {
                    GuardianListScreen(
                        viewModel = guardianListViewModel,
                        onGuardianClick = { linked ->
                            navController.navigate(Routes.guardianDetail(linked.id))
                        },
                    )
                }
                composable(Routes.GUARDIAN_DETAIL) { backStack ->
                    val linkId = backStack.arguments?.getString("linkId").orEmpty()
                    val listState by guardianListViewModel.state.collectAsStateWithLifecycle()
                    val guardian = listState.guardians.firstOrNull { it.id == linkId }
                    if (guardian == null) {
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
                composable(Routes.GUARDIAN_RECORD_DETAIL) { backStack ->
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
            }

            // ---- Plans ----------------------------------------------------
            navigation(startDestination = Routes.PLANS, route = Routes.PLANS_GRAPH) {
                composable(Routes.PLANS) {
                    PlanListScreen(
                        viewModel = trainerPlanViewModel,
                        onPlanClick = { plan -> navController.navigate(Routes.planDetail(plan.id)) },
                    )
                }
                composable(Routes.PLAN_DETAIL) { backStack ->
                    val planId = backStack.arguments?.getString("planId").orEmpty()
                    PlanDetailScreen(
                        planId = planId,
                        viewModel = trainerPlanViewModel,
                        onBack = { navController.popBackStack() },
                        onBehaviorClick = { behavior ->
                            navController.navigate(Routes.behaviorDetail(planId, behavior.id))
                        },
                    )
                }
                composable(Routes.BEHAVIOR_DETAIL) { backStack ->
                    val planId = backStack.arguments?.getString("planId").orEmpty()
                    val behaviorId = backStack.arguments?.getString("behaviorId").orEmpty()
                    BehaviorDetailScreen(
                        planId = planId,
                        behaviorId = behaviorId,
                        viewModel = trainerPlanViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // ---- Invite ---------------------------------------------------
            navigation(startDestination = Routes.INVITE, route = Routes.INVITE_GRAPH) {
                composable(Routes.INVITE) {
                    InviteScreen(viewModel = inviteViewModel)
                }
            }

            // ---- Settings -------------------------------------------------
            navigation(startDestination = Routes.SETTINGS, route = Routes.SETTINGS_GRAPH) {
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
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val parentRoute = backStack?.destination?.parent?.route
    NavigationBar {
        tabs.forEach { tab ->
            val selected = parentRoute == tab.graphRoute
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.graphRoute) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
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
