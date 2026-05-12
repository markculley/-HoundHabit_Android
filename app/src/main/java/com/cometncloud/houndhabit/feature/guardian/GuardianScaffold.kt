package com.cometncloud.houndhabit.feature.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.cometncloud.houndhabit.feature.guardian.dashboard.DashboardScreen
import com.cometncloud.houndhabit.feature.guardian.pets.PetDetailScreen
import com.cometncloud.houndhabit.feature.guardian.pets.PetListScreen
import com.cometncloud.houndhabit.feature.guardian.pets.PetViewModel
import com.cometncloud.houndhabit.feature.guardian.plans.GuardianPlanDetailScreen
import com.cometncloud.houndhabit.feature.guardian.plans.GuardianPlanListScreen
import com.cometncloud.houndhabit.feature.guardian.plans.GuardianPlanViewModel
import com.cometncloud.houndhabit.feature.guardian.plans.OwnedPlanDetailScreen
import com.cometncloud.houndhabit.feature.guardian.records.PetFilter
import com.cometncloud.houndhabit.feature.guardian.records.TrainingRecordDetailScreen
import com.cometncloud.houndhabit.feature.guardian.records.TrainingRecordListScreen
import com.cometncloud.houndhabit.feature.guardian.records.TrainingRecordViewModel
import com.cometncloud.houndhabit.feature.guardian.resources.ResourceDetailScreen
import com.cometncloud.houndhabit.feature.guardian.resources.ResourceListScreen
import com.cometncloud.houndhabit.feature.guardian.resources.ResourceViewModel
import com.cometncloud.houndhabit.feature.guardian.settings.EnterInviteCodeScreen
import com.cometncloud.houndhabit.feature.guardian.settings.SettingsScreen
import com.cometncloud.houndhabit.feature.trainer.plans.BehaviorDetailScreen as TrainerBehaviorDetailScreen
import com.cometncloud.houndhabit.feature.trainer.plans.TrainerPlanViewModel

/**
 * Routes are organized by tab subgraph. Each tab is a nested `navigation()`
 * block, which gives us:
 *  - Per-tab back stack preservation across tab switches (saveState/restoreState).
 *  - A correct bottom-bar highlight that follows the destination's *parent
 *    graph*, regardless of which tab the user came from.
 *  - Cross-tab flows (e.g., a plan opened from a pet's detail) get their own
 *    destination instance inside the originating tab's subgraph, so they
 *    don't bleed into the other tab's saved state.
 */
private object Routes {
    // Graph routes — the bottom bar navigates between these.
    const val HOME_GRAPH = "home_graph"
    const val PETS_GRAPH = "pets_graph"
    const val PLANS_GRAPH = "plans_graph"
    const val RESOURCES_GRAPH = "resources_graph"
    const val SETTINGS_GRAPH = "settings_graph"

    // Home subgraph
    const val HOME = "home"

    // Pets subgraph
    const val PETS = "pets"
    const val PETS_DETAIL = "pets/detail/{petId}"
    const val PET_SESSIONS = "pets/sessions/{petId}?name={name}"
    const val PETS_SESSION_DETAIL = "pets/session/{recordId}"
    // Cross-tab destinations reached *via* the Pets tab — distinct from the
    // Plans-tab equivalents so each tab keeps an isolated back stack.
    const val PETS_PLAN_DETAIL = "pets/plan/{assignedPlanId}"
    const val PETS_OWN_PLAN_DETAIL = "pets/own-plan/{planId}"
    const val PETS_OWN_BEHAVIOR_DETAIL = "pets/own-plan/{planId}/behavior/{behaviorId}"

    // Plans subgraph
    const val PLANS = "plans"
    const val PLAN_DETAIL = "plans/detail/{assignedPlanId}"
    const val OWN_PLAN_DETAIL = "plans/own/{planId}"
    const val OWN_BEHAVIOR_DETAIL = "plans/own/{planId}/behavior/{behaviorId}"

    // Resources subgraph
    const val RESOURCES = "resources"
    const val RESOURCE_DETAIL = "resources/detail/{resourceId}"

    // Settings subgraph
    const val SETTINGS = "settings"
    const val ENTER_INVITE_CODE = "settings/enter-code"

    fun petDetail(id: String) = "pets/detail/$id"
    fun petSessions(petId: String, petName: String) =
        "pets/sessions/$petId?name=${java.net.URLEncoder.encode(petName, "UTF-8")}"
    fun petsSessionDetail(recordId: String) = "pets/session/$recordId"
    fun petsPlanDetail(assignedPlanId: String) = "pets/plan/$assignedPlanId"
    fun petsOwnPlanDetail(planId: String) = "pets/own-plan/$planId"
    fun petsOwnBehaviorDetail(planId: String, behaviorId: String) =
        "pets/own-plan/$planId/behavior/$behaviorId"

    fun planDetail(assignedPlanId: String) = "plans/detail/$assignedPlanId"
    fun ownPlanDetail(planId: String) = "plans/own/$planId"
    fun ownBehaviorDetail(planId: String, behaviorId: String) =
        "plans/own/$planId/behavior/$behaviorId"

    fun resourceDetail(resourceId: String) = "resources/detail/$resourceId"
}

private data class GuardianTab(
    val graphRoute: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    GuardianTab(Routes.HOME_GRAPH, "Home", Icons.Filled.Home),
    GuardianTab(Routes.PETS_GRAPH, "Pets", Icons.Filled.Pets),
    GuardianTab(Routes.PLANS_GRAPH, "Plans", Icons.AutoMirrored.Filled.ListAlt),
    GuardianTab(Routes.RESOURCES_GRAPH, "Resources", Icons.Filled.Folder),
    GuardianTab(Routes.SETTINGS_GRAPH, "Settings", Icons.Filled.Settings),
)

@Composable
fun GuardianScaffold(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    // Scaffold-scoped VMs so list + detail destinations across both tab
    // entries share state (e.g., a plan-detail viewed via Pets and via Plans
    // sees the same items map).
    val petViewModel: PetViewModel = viewModel()
    val recordViewModel: TrainingRecordViewModel = viewModel()
    val resourceViewModel: ResourceViewModel = viewModel()
    val planViewModel: GuardianPlanViewModel = viewModel()
    val ownPlanViewModel: TrainerPlanViewModel = viewModel()

    Scaffold(
        bottomBar = { BottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME_GRAPH,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ---- Home -----------------------------------------------------
            navigation(startDestination = Routes.HOME, route = Routes.HOME_GRAPH) {
                composable(Routes.HOME) { DashboardScreen() }
            }

            // ---- Pets -----------------------------------------------------
            navigation(startDestination = Routes.PETS, route = Routes.PETS_GRAPH) {
                composable(Routes.PETS) {
                    PetListScreen(
                        viewModel = petViewModel,
                        onPetClick = { pet -> navController.navigate(Routes.petDetail(pet.id)) },
                    )
                }
                composable(Routes.PETS_DETAIL) { backStack ->
                    val petId = backStack.arguments?.getString("petId").orEmpty()
                    val petName = petViewModel.state.value.pets.firstOrNull { it.id == petId }?.name
                    PetDetailScreen(
                        petId = petId,
                        viewModel = petViewModel,
                        planViewModel = planViewModel,
                        onBack = { navController.popBackStack() },
                        onTrainingSessions = if (petName != null) {
                            { navController.navigate(Routes.petSessions(petId, petName)) }
                        } else null,
                        // Cross-tab navigation stays inside the Pets subgraph.
                        onAssignedPlanClick = { ap ->
                            navController.navigate(Routes.petsPlanDetail(ap.id))
                        },
                        onOwnPlanClick = { plan ->
                            navController.navigate(Routes.petsOwnPlanDetail(plan.id))
                        },
                    )
                }
                composable(Routes.PET_SESSIONS) { backStack ->
                    val petId = backStack.arguments?.getString("petId").orEmpty()
                    val petName = backStack.arguments?.getString("name")
                        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                        .orEmpty()
                    TrainingRecordListScreen(
                        viewModel = recordViewModel,
                        petFilter = PetFilter(petId = petId, petName = petName),
                        onBack = { navController.popBackStack() },
                        onRecordClick = { record ->
                            navController.navigate(Routes.petsSessionDetail(record.id))
                        },
                    )
                }
                composable(Routes.PETS_SESSION_DETAIL) { backStack ->
                    val recordId = backStack.arguments?.getString("recordId").orEmpty()
                    TrainingRecordDetailScreen(
                        recordId = recordId,
                        viewModel = recordViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.PETS_PLAN_DETAIL) { backStack ->
                    val id = backStack.arguments?.getString("assignedPlanId").orEmpty()
                    GuardianPlanDetailScreen(
                        assignedPlanId = id,
                        viewModel = planViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.PETS_OWN_PLAN_DETAIL) { backStack ->
                    val planId = backStack.arguments?.getString("planId").orEmpty()
                    val pets = petViewModel.state.collectAsState().value.pets
                    OwnedPlanDetailScreen(
                        planId = planId,
                        planViewModel = planViewModel,
                        pets = pets,
                        viewModel = ownPlanViewModel,
                        onBack = { navController.popBackStack() },
                        onBehaviorClick = { behavior ->
                            navController.navigate(Routes.petsOwnBehaviorDetail(planId, behavior.id))
                        },
                    )
                }
                composable(Routes.PETS_OWN_BEHAVIOR_DETAIL) { backStack ->
                    val planId = backStack.arguments?.getString("planId").orEmpty()
                    val behaviorId = backStack.arguments?.getString("behaviorId").orEmpty()
                    TrainerBehaviorDetailScreen(
                        planId = planId,
                        behaviorId = behaviorId,
                        viewModel = ownPlanViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // ---- Plans ----------------------------------------------------
            navigation(startDestination = Routes.PLANS, route = Routes.PLANS_GRAPH) {
                composable(Routes.PLANS) {
                    GuardianPlanListScreen(
                        viewModel = planViewModel,
                        petViewModel = petViewModel,
                        onAssignedPlanClick = { ap -> navController.navigate(Routes.planDetail(ap.id)) },
                        onOwnPlanClick = { plan -> navController.navigate(Routes.ownPlanDetail(plan.id)) },
                    )
                }
                composable(Routes.PLAN_DETAIL) { backStack ->
                    val id = backStack.arguments?.getString("assignedPlanId").orEmpty()
                    GuardianPlanDetailScreen(
                        assignedPlanId = id,
                        viewModel = planViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.OWN_PLAN_DETAIL) { backStack ->
                    val planId = backStack.arguments?.getString("planId").orEmpty()
                    val pets = petViewModel.state.collectAsState().value.pets
                    OwnedPlanDetailScreen(
                        planId = planId,
                        planViewModel = planViewModel,
                        pets = pets,
                        viewModel = ownPlanViewModel,
                        onBack = { navController.popBackStack() },
                        onBehaviorClick = { behavior ->
                            navController.navigate(Routes.ownBehaviorDetail(planId, behavior.id))
                        },
                    )
                }
                composable(Routes.OWN_BEHAVIOR_DETAIL) { backStack ->
                    val planId = backStack.arguments?.getString("planId").orEmpty()
                    val behaviorId = backStack.arguments?.getString("behaviorId").orEmpty()
                    TrainerBehaviorDetailScreen(
                        planId = planId,
                        behaviorId = behaviorId,
                        viewModel = ownPlanViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // ---- Resources ------------------------------------------------
            navigation(startDestination = Routes.RESOURCES, route = Routes.RESOURCES_GRAPH) {
                composable(Routes.RESOURCES) {
                    ResourceListScreen(
                        viewModel = resourceViewModel,
                        onResourceClick = { resource ->
                            navController.navigate(Routes.resourceDetail(resource.id))
                        },
                    )
                }
                composable(Routes.RESOURCE_DETAIL) { backStack ->
                    val resourceId = backStack.arguments?.getString("resourceId").orEmpty()
                    ResourceDetailScreen(
                        resourceId = resourceId,
                        viewModel = resourceViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // ---- Settings -------------------------------------------------
            navigation(startDestination = Routes.SETTINGS, route = Routes.SETTINGS_GRAPH) {
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        isTrainer = false,
                        onSignOut = onSignOut,
                        onEnterInviteCode = { navController.navigate(Routes.ENTER_INVITE_CODE) },
                    )
                }
                composable(Routes.ENTER_INVITE_CODE) {
                    EnterInviteCodeScreen(
                        onDone = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    // Highlight the tab whose subgraph contains the current destination —
    // works automatically across cross-tab flows.
    val parentRoute = backStack?.destination?.parent?.route

    NavigationBar {
        tabs.forEach { tab ->
            val selected = parentRoute == tab.graphRoute
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.graphRoute) {
                        // Pop everything back to the root start destination,
                        // saving each tab's stack so re-entering restores it.
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
