# Porting Map: iOS → Android

Maps each Swift source file in this repo to its Android Kotlin counterpart. Folder layout follows a feature-grouped MVVM structure analogous to the iOS one.

## Stack equivalences

| iOS concept | Android concept |
|---|---|
| SwiftUI `View` | Jetpack Compose `@Composable fun` |
| `@Observable` ViewModel | `androidx.lifecycle.ViewModel` + `StateFlow` (or Compose `mutableStateOf`) |
| `async throws` Service struct | `suspend fun` in a class, errors via `Result<T>` or thrown exceptions |
| `Codable` + `CodingKeys` | `kotlinx.serialization` `@Serializable` + `@SerialName` |
| `NavigationStack` + value-based `NavigationLink` | `NavHost` + typed routes (Navigation Compose 2.8+ type-safe routes) |
| `.sheet { … }` | `ModalBottomSheet` or full-screen `Dialog` |
| `Task { … }` | `viewModelScope.launch { … }` |
| `UNUserNotificationCenter` | `WorkManager` + `NotificationCompat` |
| `PhotosPicker` | Photo Picker via `ActivityResultContracts.PickVisualMedia` |
| Apple Sign-In | Google Sign-In via Credential Manager |

## App entry & routing

| iOS | Android |
|---|---|
| [HoundHabitApp.swift](../../HoundHabit/HoundHabitApp.swift) | `MainActivity.kt` + `HoundHabitApp.kt` (`@Composable` root) |
| [AppRouter.swift](../../HoundHabit/AppRouter.swift) | `AppRouter.kt` — observes `supabase.auth.sessionStatus` flow, switches between `AuthGraph` / `GuardianGraph` / `TrainerGraph` `NavHost`s |
| [ContentView.swift](../../HoundHabit/ContentView.swift) | `RootScaffold.kt` |
| [SplashView.swift](../../HoundHabit/SplashView.swift) | Use a Splash Screen API (`androidx.core:core-splashscreen`); no Compose file needed |
| [ErrorStore.swift](../../HoundHabit/ErrorStore.swift) | `ErrorStore.kt` — `MutableStateFlow<String?>`, surfaced via a snackbar host in `RootScaffold` |

## Core / Models

`kotlinx.serialization` with `@SerialName("snake_case")` replaces `CodingKeys`. UUIDs use `kotlin.uuid.Uuid` (stable in Kotlin 2.0+) or `java.util.UUID`. Dates use `kotlinx-datetime` (`Instant`, `LocalDate`).

| iOS | Android |
|---|---|
| [Badge.swift](../../HoundHabit/Core/Models/Badge.swift) | `core/models/Badge.kt` |
| [Behavior.swift](../../HoundHabit/Core/Models/Behavior.swift) | `core/models/Behavior.kt` |
| [Comment.swift](../../HoundHabit/Core/Models/Comment.swift) | `core/models/Comment.kt` |
| [Invite.swift](../../HoundHabit/Core/Models/Invite.swift) | `core/models/Invite.kt` |
| [Pet.swift](../../HoundHabit/Core/Models/Pet.swift) | `core/models/Pet.kt` |
| [PlanAssignment.swift](../../HoundHabit/Core/Models/PlanAssignment.swift) | `core/models/PlanAssignment.kt` |
| [Profile.swift](../../HoundHabit/Core/Models/Profile.swift) | `core/models/Profile.kt` (enum `Role`) |
| [Resource.swift](../../HoundHabit/Core/Models/Resource.swift) | `core/models/Resource.kt` |
| [TrainerGuardianLink.swift](../../HoundHabit/Core/Models/TrainerGuardianLink.swift) | `core/models/TrainerGuardianLink.kt` |
| [TrainingPlan.swift](../../HoundHabit/Core/Models/TrainingPlan.swift) | `core/models/TrainingPlan.kt` |
| [TrainingPlanItem.swift](../../HoundHabit/Core/Models/TrainingPlanItem.swift) | `core/models/TrainingPlanItem.kt` |
| [TrainingRecord.swift](../../HoundHabit/Core/Models/TrainingRecord.swift) | `core/models/TrainingRecord.kt` |

## Core / Services

| iOS | Android |
|---|---|
| [SupabaseClient.swift](../../HoundHabit/Core/Services/SupabaseClient.swift) | `core/SupabaseClient.kt` — `createSupabaseClient(url, anonKey) { install(Auth); install(Postgrest); install(Storage) }` |
| [AuthService.swift](../../HoundHabit/Core/Services/AuthService.swift) | `core/services/AuthService.kt`. Note: `delete_my_account` RPC is reused as-is. **Apply same fix already in iOS:** don't sign out inside `deleteAccount()` — let the caller signOut after showing confirmation. |
| [BadgeService.swift](../../HoundHabit/Core/Services/BadgeService.swift) | `core/services/BadgeService.kt` |
| [CommentService.swift](../../HoundHabit/Core/Services/CommentService.swift) | `core/services/CommentService.kt` |
| [InviteService.swift](../../HoundHabit/Core/Services/InviteService.swift) | `core/services/InviteService.kt` |
| [PetService.swift](../../HoundHabit/Core/Services/PetService.swift) | `core/services/PetService.kt` |
| [ResourceService.swift](../../HoundHabit/Core/Services/ResourceService.swift) | `core/services/ResourceService.kt` |
| [StorageService.swift](../../HoundHabit/Core/Services/StorageService.swift) | `core/services/StorageService.kt` — `supabase.storage.from(bucket).upload(...)`. Path lowercasing rule from CLAUDE.md applies identically. |
| [TrainingPlanService.swift](../../HoundHabit/Core/Services/TrainingPlanService.swift) | `core/services/TrainingPlanService.kt` |
| [TrainingRecordService.swift](../../HoundHabit/Core/Services/TrainingRecordService.swift) | `core/services/TrainingRecordService.kt` |

## Auth flow

| iOS | Android |
|---|---|
| [AuthViewModel.swift](../../HoundHabit/Core/Auth/AuthViewModel.swift) | `feature/auth/AuthViewModel.kt` |
| [LoginView.swift](../../HoundHabit/Core/Auth/LoginView.swift) | `feature/auth/LoginScreen.kt` |
| [SignUpView.swift](../../HoundHabit/Core/Auth/SignUpView.swift) | `feature/auth/SignUpScreen.kt` |
| [RoleSelectionView.swift](../../HoundHabit/Core/Auth/RoleSelectionView.swift) | `feature/auth/RoleSelectionScreen.kt` |

## Guardian feature set

| iOS | Android |
|---|---|
| [GuardianTabView.swift](../../HoundHabit/Guardian/GuardianTabView.swift) | `feature/guardian/GuardianScaffold.kt` — `NavigationBar` with the same tabs |
| [DashboardView.swift](../../HoundHabit/Guardian/Dashboard/DashboardView.swift) + [DashboardViewModel.swift](../../HoundHabit/Guardian/Dashboard/DashboardViewModel.swift) | `feature/guardian/dashboard/DashboardScreen.kt` + `DashboardViewModel.kt` |
| [AchievementsView.swift](../../HoundHabit/Guardian/Achievements/AchievementsView.swift) | `feature/guardian/achievements/AchievementsScreen.kt` |
| [PetListView.swift](../../HoundHabit/Guardian/Pets/PetListView.swift) | `feature/guardian/pets/PetListScreen.kt` |
| [PetDetailView.swift](../../HoundHabit/Guardian/Pets/PetDetailView.swift) | `feature/guardian/pets/PetDetailScreen.kt` |
| [PetFormView.swift](../../HoundHabit/Guardian/Pets/PetFormView.swift) | `feature/guardian/pets/PetFormScreen.kt` |
| [PetAvatarView.swift](../../HoundHabit/Guardian/Pets/PetAvatarView.swift) | `feature/guardian/pets/PetAvatar.kt` |
| [PetViewModel.swift](../../HoundHabit/Guardian/Pets/PetViewModel.swift) | `feature/guardian/pets/PetViewModel.kt` |
| [TrainingRecordListView.swift](../../HoundHabit/Guardian/TrainingRecords/TrainingRecordListView.swift) | `feature/guardian/records/RecordListScreen.kt` |
| [TrainingRecordDetailView.swift](../../HoundHabit/Guardian/TrainingRecords/TrainingRecordDetailView.swift) | `feature/guardian/records/RecordDetailScreen.kt` |
| [TrainingRecordFormView.swift](../../HoundHabit/Guardian/TrainingRecords/TrainingRecordFormView.swift) | `feature/guardian/records/RecordFormScreen.kt` |
| [TrainingRecordViewModel.swift](../../HoundHabit/Guardian/TrainingRecords/TrainingRecordViewModel.swift) | `feature/guardian/records/RecordViewModel.kt` |
| [GuardianPlanListView.swift](../../HoundHabit/Guardian/Plans/GuardianPlanListView.swift) | `feature/guardian/plans/PlanListScreen.kt` |
| [GuardianPlanDetailView.swift](../../HoundHabit/Guardian/Plans/GuardianPlanDetailView.swift) | `feature/guardian/plans/PlanDetailScreen.kt` |
| [GuardianPlanViewModel.swift](../../HoundHabit/Guardian/Plans/GuardianPlanViewModel.swift) | `feature/guardian/plans/PlanViewModel.kt` |
| [ResourceListView.swift](../../HoundHabit/Guardian/Resources/ResourceListView.swift) | `feature/guardian/resources/ResourceListScreen.kt` |
| [ResourceDetailView.swift](../../HoundHabit/Guardian/Resources/ResourceDetailView.swift) | `feature/guardian/resources/ResourceDetailScreen.kt` |
| [ResourceFormView.swift](../../HoundHabit/Guardian/Resources/ResourceFormView.swift) | `feature/guardian/resources/ResourceFormScreen.kt` |
| [SettingsView.swift](../../HoundHabit/Guardian/Settings/SettingsView.swift) | `feature/guardian/settings/SettingsScreen.kt` |
| [AccountView.swift](../../HoundHabit/Guardian/Settings/AccountView.swift) | `feature/guardian/settings/AccountScreen.kt`. Replicate the "Account Deleted" confirmation alert added recently. |
| [EnterInviteCodeView.swift](../../HoundHabit/Guardian/Settings/EnterInviteCodeView.swift) | `feature/guardian/settings/EnterInviteCodeScreen.kt` |
| [NotificationSettingsView.swift](../../HoundHabit/Guardian/Settings/NotificationSettingsView.swift) | `feature/guardian/settings/NotificationSettingsScreen.kt` |

## Trainer feature set

The iOS repo currently has duplicated Plan files at both `Trainer/` and `Trainer/Plans/` (the older non-`Plans/` set appears unused). Port the canonical versions under `Trainer/Plans/` and skip the duplicates.

| iOS | Android |
|---|---|
| [TrainerTabView.swift](../../HoundHabit/Trainer/TrainerTabView.swift) | `feature/trainer/TrainerScaffold.kt` |
| [GuardianListView.swift](../../HoundHabit/Trainer/Guardians/GuardianListView.swift) | `feature/trainer/guardians/GuardianListScreen.kt` |
| [GuardianDetailView.swift](../../HoundHabit/Trainer/Guardians/GuardianDetailView.swift) | `feature/trainer/guardians/GuardianDetailScreen.kt` |
| [TrainerAddResourceView.swift](../../HoundHabit/Trainer/Guardians/TrainerAddResourceView.swift) | `feature/trainer/guardians/AddResourceScreen.kt` |
| [InviteView.swift](../../HoundHabit/Trainer/Invites/InviteView.swift) | `feature/trainer/invites/InviteScreen.kt` |
| [TrainerPlanListView.swift](../../HoundHabit/Trainer/Plans/TrainerPlanListView.swift) | `feature/trainer/plans/PlanListScreen.kt` |
| [TrainerPlanDetailView.swift](../../HoundHabit/Trainer/Plans/TrainerPlanDetailView.swift) | `feature/trainer/plans/PlanDetailScreen.kt` |
| [TrainerPlanFormView.swift](../../HoundHabit/Trainer/Plans/TrainerPlanFormView.swift) | `feature/trainer/plans/PlanFormScreen.kt` |
| [TrainerPlanItemFormView.swift](../../HoundHabit/Trainer/Plans/TrainerPlanItemFormView.swift) | `feature/trainer/plans/PlanItemFormScreen.kt` |
| [TrainerBehaviorDetailView.swift](../../HoundHabit/Trainer/Plans/TrainerBehaviorDetailView.swift) | `feature/trainer/plans/BehaviorDetailScreen.kt` |
| [TrainerBehaviorFormView.swift](../../HoundHabit/Trainer/Plans/TrainerBehaviorFormView.swift) | `feature/trainer/plans/BehaviorFormScreen.kt` |
| [TrainerPlanViewModel.swift](../../HoundHabit/Trainer/Plans/TrainerPlanViewModel.swift) | `feature/trainer/plans/PlanViewModel.kt` |
| [AssignPlanSheet.swift](../../HoundHabit/Trainer/Plans/AssignPlanSheet.swift) | `feature/trainer/plans/AssignPlanSheet.kt` (`ModalBottomSheet`) |

## Shared

| iOS | Android |
|---|---|
| [PhotoPickerView.swift](../../HoundHabit/Shared/Components/PhotoPickerView.swift) | Use `rememberLauncherForActivityResult(PickVisualMedia())` directly in callers; no wrapper component needed |
| [SafariView.swift](../../HoundHabit/Shared/Components/SafariView.swift) | `Intent(Intent.ACTION_VIEW, uri)` via Custom Tabs (`androidx.browser:browser`) |
| [StatusBadgeView.swift](../../HoundHabit/Shared/Components/StatusBadgeView.swift) | `shared/components/StatusBadge.kt` (Compose) |
| [TimerView.swift](../../HoundHabit/Shared/Components/TimerView.swift) | `shared/components/Timer.kt` |
| [HapticManager.swift](../../HoundHabit/Shared/Utilities/HapticManager.swift) | `shared/util/Haptics.kt` — `view.performHapticFeedback(...)` |
| [NotificationManager.swift](../../HoundHabit/Shared/Utilities/NotificationManager.swift) | `shared/notifications/NotificationManager.kt` — schedule via `WorkManager` `PeriodicWorkRequest` (or `AlarmManager` with exact alarms if precise time-of-day matters), display via `NotificationCompat.Builder` |

## Gotchas worth carrying over

1. **UUID lowercasing** — Supabase RLS uses `auth.uid()::text` which is lowercase. Storage paths and any string-compared UUIDs must `.lowercase()` (Kotlin: `uuid.toString().lowercase()`).
2. **No nested NavHosts inside the same `NavController`** — analogous to the iOS rule about not nesting `NavigationStack`s. Use nested graphs (`navigation { ... }`) inside one `NavHost`, not two `NavHost`s.
3. **Account deletion** — keep the recently-added flow: RPC first, show confirmation alert, sign out only on dismiss. Don't auto-sign-out inside the service method.
4. **Profile auto-creation** — handled by the existing Postgres trigger on `auth.users`; pass `full_name` and `role` in user metadata at sign-up time exactly as the iOS `signUp` does.
5. **Tests** — Android equivalent of the iOS testing rules: write tests for `@Serializable` enum decoding (one test per enum, asserting the exact wire string maps to the expected case) and for pure ViewModel logic (streaks, filters, aggregations). Skip Composable UI tests and Service-layer tests for now — same as iOS.
