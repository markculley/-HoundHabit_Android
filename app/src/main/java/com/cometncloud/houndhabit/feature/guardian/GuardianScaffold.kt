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
import androidx.compose.material3.TextButton
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
import com.cometncloud.houndhabit.feature.guardian.pets.PetDetailScreen
import com.cometncloud.houndhabit.feature.guardian.pets.PetListScreen
import com.cometncloud.houndhabit.feature.guardian.pets.PetViewModel
import com.cometncloud.houndhabit.feature.guardian.records.PetFilter
import com.cometncloud.houndhabit.feature.guardian.records.TrainingRecordDetailScreen
import com.cometncloud.houndhabit.feature.guardian.records.TrainingRecordListScreen
import com.cometncloud.houndhabit.feature.guardian.records.TrainingRecordViewModel

private object Routes {
    const val HOME = "home"
    const val PETS = "pets"
    const val PETS_DETAIL = "pets/detail"
    const val PET_SESSIONS = "pet/sessions"
    const val SESSION_DETAIL = "sessions/detail"
    const val PLANS = "plans"
    const val RESOURCES = "resources"
    const val SETTINGS = "settings"

    fun petDetail(id: String) = "$PETS_DETAIL/$id"
    fun petSessions(petId: String, petName: String) =
        "$PET_SESSIONS/$petId?name=${java.net.URLEncoder.encode(petName, "UTF-8")}"
    fun sessionDetail(recordId: String) = "$SESSION_DETAIL/$recordId"
}

private data class GuardianTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    GuardianTab(Routes.HOME, "Home", Icons.Filled.Home),
    GuardianTab(Routes.PETS, "Pets", Icons.Filled.Pets),
    GuardianTab(Routes.PLANS, "Plans", Icons.AutoMirrored.Filled.ListAlt),
    GuardianTab(Routes.RESOURCES, "Resources", Icons.Filled.Folder),
    GuardianTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun GuardianScaffold(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    // Scope shared ViewModels above the NavHost so list + detail screens share the same instance.
    val petViewModel: PetViewModel = viewModel()
    val recordViewModel: TrainingRecordViewModel = viewModel()

    Scaffold(
        bottomBar = { BottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Routes.HOME) { ComingSoon("Home", "Streaks and dashboard arrive in Phase 5.") }

            composable(Routes.PETS) {
                PetListScreen(
                    viewModel = petViewModel,
                    onPetClick = { pet -> navController.navigate(Routes.petDetail(pet.id)) },
                )
            }
            composable(
                route = "${Routes.PETS_DETAIL}/{petId}",
            ) { backStack ->
                val petId = backStack.arguments?.getString("petId").orEmpty()
                val petName = petViewModel.state.value.pets.firstOrNull { it.id == petId }?.name
                PetDetailScreen(
                    petId = petId,
                    viewModel = petViewModel,
                    onBack = { navController.popBackStack() },
                    onTrainingSessions = if (petName != null) {
                        { navController.navigate(Routes.petSessions(petId, petName)) }
                    } else null,
                )
            }
            composable(
                route = "${Routes.PET_SESSIONS}/{petId}?name={name}",
            ) { backStack ->
                val petId = backStack.arguments?.getString("petId").orEmpty()
                val petName = backStack.arguments?.getString("name")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    .orEmpty()
                TrainingRecordListScreen(
                    viewModel = recordViewModel,
                    petFilter = PetFilter(petId = petId, petName = petName),
                    onBack = { navController.popBackStack() },
                    onRecordClick = { record ->
                        navController.navigate(Routes.sessionDetail(record.id))
                    },
                )
            }
            composable(
                route = "${Routes.SESSION_DETAIL}/{recordId}",
            ) { backStack ->
                val recordId = backStack.arguments?.getString("recordId").orEmpty()
                TrainingRecordDetailScreen(
                    recordId = recordId,
                    viewModel = recordViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.PLANS) { ComingSoon("Plans", "Training plans arrive in Phase 9.") }
            composable(Routes.RESOURCES) { ComingSoon("Resources", "Resources arrive in Phase 6.") }
            composable(Routes.SETTINGS) { SettingsPlaceholder(onSignOut) }
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
                (tab.route == Routes.PETS && (
                    currentRoute?.startsWith(Routes.PETS_DETAIL) == true ||
                        currentRoute?.startsWith(Routes.PET_SESSIONS) == true ||
                        currentRoute?.startsWith(Routes.SESSION_DETAIL) == true
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

@Composable
private fun SettingsPlaceholder(onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Account, notifications, trainer linking arrive in later phases.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onSignOut) { Text("Sign out") }
    }
}
