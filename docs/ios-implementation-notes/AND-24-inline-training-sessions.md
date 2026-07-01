# AND-24 — Inline Training Sessions on Pet detail; remove separate list screen

Notes from the iOS implementation (card **IOS-20**), which **also folded in IOS-19** (header style match). The sibling Android card **AND-23** ("Match Training Sessions header style to Plans") should be closed at the same time as AND-24 — restyling the header is inherent to inlining the list.

## Cards

- **Android**: [AND-24](https://www.notion.so/35ed8a41762281a895d3e724f174b8a2) (inline) + [AND-23](https://www.notion.so/35ed8a417622815ab2bad02054fe2408) (header style — folded in)
- **iOS**: [IOS-20](https://www.notion.so/35ed8a417622815e8d74d5807c80d1f3) + [IOS-19](https://www.notion.so/35ed8a4176228138bbfdd43ea834393d) — shipped together

## What iOS did

Three file touches:

| iOS file | Android equivalent |
|---|---|
| `HoundHabit/Guardian/Pets/PetDetailView.swift` | `feature/guardian/pets/PetDetailScreen.kt` |
| `HoundHabit/Guardian/TrainingRecords/TrainingRecordListView.swift` (deleted) | `feature/guardian/records/TrainingRecordListScreen.kt` (delete) |
| `HoundHabit/Guardian/TrainingRecords/TrainingRecordRow.swift` (new — extracted) | `feature/guardian/records/TrainingRecordRow.kt` (extract — also used by `GuardianDetailScreen`) |

### 1. Extract the row composable first

`TrainingRecordRow` was a `private struct` inside the deleted list view, but it's **also used by the trainer's `GuardianDetailView`**. Before deleting the list view, pull the row into its own file so the trainer view keeps compiling. On Android, do the same: extract the record-row composable from `TrainingRecordListScreen.kt` to its own file (or to a shared `feature/guardian/records/components/`) — `GuardianDetailScreen.kt` references it too.

### 2. Convert PetDetailScreen to a single scrollable list

iOS converted the `ScrollView { VStack { hero; plansSection; trainingSessionsButton } }` body to a single `List` with three sections. Why: the new Training Sessions section needs native `.swipeActions` for share/delete, which only work inside `List`. The hero and plans sections preserve their custom-card visuals via `.listRowInsets(EdgeInsets())` + `.listRowSeparator(.hidden)` + `.listRowBackground(Color.clear)`.

For Compose: keep `LazyColumn` as the outer scroll container. Hero and plans are `item { ... }` blocks; training sessions becomes a sequence of `item { header }` + `items(records) { record -> ... }`. Compose doesn't need a special "row inset reset" because `LazyColumn` items don't impose List-style separators or insets to begin with.

### 3. Training Sessions header — matches Plans (this is the AND-23 piece)

iOS replaced the centered full-width clipboard button with a row that mirrors the Plans header at `PetDetailScreen.kt:162-189`:

```swift
HStack {
    Text("Training Sessions")
        .font(.title2).bold()
    Spacer()
    Button { showLogSessionSheet = true } label: {
        Image(systemName: "plus.circle.fill")
            .font(.title2)
            .foregroundStyle(Color.accentColor)
    }
}
```

Plans on iOS uses a `Menu` with two items ("New Plan", "Assign Existing Plan"). Training Sessions has only one action (log a session), so iOS used a plain `Button` — same icon (`plus.circle.fill`), same font, no menu wrapper. Android can do the same: an `IconButton(onClick = ...) { Icon(Icons.Default.AddCircle, ...) }` next to `Text("Training Sessions", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))`.

### 4. Inline the records list

Inside the Training Sessions section iOS renders, in order:

- `ProgressView` row while `trainingVM.isLoading && trainingVM.records.isEmpty`
- Empty-state `Text("No sessions yet. Tap + to log one.")` when records are empty (no big `ContentUnavailableView`)
- Otherwise, a `ForEach(trainingVM.records) { record in ... }` with each row in a `NavigationLink(value: record)` plus `.swipeActions` for share (leading) and delete (trailing)

For Compose, wrap rows in a `SwipeToDismissBox` or use the Material 3 swipe-to-action pattern. Tap → `navController.navigate(...)` to record detail. Empty state is just a `Text(...)`.

### 5. Tap-to-detail = push, not sheet

iOS replaced the old "open list as a sheet" pattern with a push: `navigationDestination(for: TrainingRecord.self) { ... }` on the Pet detail's `NavigationStack`. On Android, register the record-detail composable on the surrounding `NavHost` (likely `GuardianScaffold.kt`), or hoist if it currently lives in the deleted records sub-graph. The old `PET_SESSIONS` route is gone.

### 6. Pull-to-refresh refreshes both

`.refreshable` on the outer List now calls `loadPetPlans()` AND `trainingVM.loadRecords(petId:)`. On Compose, use `PullRefreshIndicator` and have its `onRefresh` call both data loaders.

### 7. Sheets/state added to PetDetailScreen

iOS added:
- `@State var trainingVM = TrainingRecordViewModel()` — Pet detail now owns its instance directly (was hidden inside the deleted list view)
- `@State var showLogSessionSheet = false`
- `.sheet(isPresented: $showLogSessionSheet) { TrainingRecordFormView(preselectedPetId: pet.id) { ... insert into trainingVM.records } }`
- An error alert binding to `trainingVM.errorMessage` (was previously only surfaced in the deleted list view)

Removed:
- `@State var showTrainingSessions` and the `.sheet` that presented `TrainingRecordListView`

### 8. Project file housekeeping

iOS swapped the pbxproj references rather than adding a new entry + deleting the old (`TrainingRecordListView.swift` → `TrainingRecordRow.swift`, same UUIDs). On Android there's no equivalent project metadata to swap — Gradle picks up files by directory scan — so just rename/delete on disk.

## Android implementation outline

```kotlin
// PetDetailScreen.kt
@Composable
fun PetDetailScreen(
    petId: UUID,
    onRecordTap: (TrainingRecord) -> Unit,
    // remove: onTrainingSessions: (UUID) -> Unit,
) {
    val petVm: PetViewModel = …
    val trainingVm: TrainingRecordViewModel = … // hoisted to this screen
    val state by trainingVm.state.collectAsStateWithLifecycle()
    var showLogSheet by remember { mutableStateOf(false) }

    LaunchedEffect(petId) { trainingVm.loadRecords(petId) }

    val pullState = rememberPullRefreshState(
        refreshing = state.isLoading,
        onRefresh = {
            scope.launch {
                petVm.loadPlans(petId)
                trainingVm.loadRecords(petId)
            }
        }
    )

    LazyColumn(Modifier.pullRefresh(pullState)) {
        item { HeroSection(pet) }
        item { PlansSection(pet, …) }

        // Training Sessions
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Training Sessions",
                     style = MaterialTheme.typography.titleLarge,
                     fontWeight = FontWeight.Bold,
                     modifier = Modifier.weight(1f))
                IconButton(onClick = { showLogSheet = true }) {
                    Icon(Icons.Filled.AddCircle, contentDescription = "Log session",
                         tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        when {
            state.isLoading && state.records.isEmpty() -> item { CircularProgressIndicator() }
            state.records.isEmpty() -> item {
                Text("No sessions yet. Tap + to log one.",
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> items(state.records, key = { it.id }) { record ->
                SwipeableRecordRow(
                    record = record,
                    onTap = { onRecordTap(record) },
                    onToggleShare = { scope.launch { trainingVm.toggleSharing(record) } },
                    onDelete = { scope.launch { trainingVm.deleteRecord(record) } },
                )
            }
        }
    }

    if (showLogSheet) {
        ModalBottomSheet(onDismissRequest = { showLogSheet = false }) {
            TrainingRecordForm(
                preselectedPetId = petId,
                onSaved = { /* prepend to trainingVm.records */; showLogSheet = false }
            )
        }
    }
}
```

## Cleanup checklist (Android)

- [ ] Delete `feature/guardian/records/TrainingRecordListScreen.kt`
- [ ] Remove the `PET_SESSIONS` route and `petSessions(...)` helper in `GuardianScaffold.kt`
- [ ] Remove the `composable(...)` block for `PET_SESSIONS`
- [ ] Remove the `onTrainingSessions` callback from `PetDetailScreen`'s signature and all call sites
- [ ] Extract the record row composable to its own file (still used by `GuardianDetailScreen`)
- [ ] Register the record-detail composable on the surrounding `NavHost` if it currently lives in a sub-graph that goes away
- [ ] AND-23 header restyle is done implicitly — close it with a note pointing at this PR

## Manual test plan

1. Open Pet detail → hero + plans look unchanged → Training Sessions header is left-aligned bold with trailing `+` icon (matches Plans).
2. Tap `+` in Training Sessions header → log form opens pre-selected to this pet → save → record prepends to the inline list.
3. Tap a record row → record detail pushes onto the same Pet detail nav stack → back swipe returns to Pet detail.
4. Swipe-leading on a record → Share/Unshare action visible and tappable → toggles `is_shared`.
5. Swipe-trailing on a record → Delete action → record disappears.
6. Empty Pet (no sessions) → see "No sessions yet. Tap + to log one." inline; no big empty-state component.
7. Pull-to-refresh on Pet detail → both plans and sessions reload.
8. Trainer's `GuardianDetailScreen` still renders sessions correctly (row composable was extracted, not deleted).

## Notes on edge cases iOS handled

- **Performance**: a pet with hundreds of sessions inlined on a scrolling page could feel heavy. Current scale doesn't warrant pagination but worth a comment in the code.
- **Row composable is shared**: don't inline it into `PetDetailScreen` — `GuardianDetailScreen` still uses it.
- **Error surface**: the deleted list screen had its own error alert. iOS moved the alert binding to `PetDetailView`. Compose: collect `trainingVm.errorMessage` and show a `SnackbarHost` or `AlertDialog` from the screen.
