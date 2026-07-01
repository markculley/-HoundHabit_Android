# AND-25 — Inline-add + auto-save for plan behaviors and steps

Notes from the parallel iOS implementation (card **IOS-21**). Pure UI/UX change — no backend.

## Cards

- **Android**: [AND-25](https://www.notion.so/35ed8a417622811ca705dfaf058d644b)
- **iOS**: [IOS-21](https://www.notion.so/35ed8a41762281c79cb2eecbedc29d7f) — shipped first

## What iOS did

Three files touched:

| iOS file | Android equivalent |
|---|---|
| `HoundHabit/Trainer/Plans/TrainerPlanDetailView.swift` | `feature/trainer/plans/PlanDetailScreen.kt` |
| `HoundHabit/Trainer/Plans/TrainerBehaviorDetailView.swift` | `feature/trainer/plans/BehaviorDetailScreen.kt` |
| `HoundHabit/Trainer/Plans/TrainerPlanItemFormView.swift` | `feature/trainer/plans/PlanItemFormScreen.kt` |

### 1. Inline `+ Add Behavior` in the plan detail

Removed "Add Behavior" from the toolbar Menu. Added a `Button(Label("Add Behavior", systemImage: "plus.circle"))` at the **bottom of the Behaviors section**, after the existing `ForEach`. Tint set to `.tint` so it reads as an action row. Tap opens the existing `TrainerBehaviorFormView` create sheet — no other changes to the create flow.

Empty-state copy nudges toward the new affordance: *"No behaviors yet. Tap + Add Behavior below to begin."*

### 2. Inline `+ Add Step` in the behavior detail

Same pattern inside `TrainerBehaviorDetailView`. Removed the toolbar Add Step button. The `+ Add Step` row sits below the steps list and tap opens the create sheet (`TrainerPlanItemFormView(mode: .add)`).

### 3. Tap-to-edit behavior name

Removed the toolbar "Rename" button and its sheet. Added an inline `Section("Name")` with a `TextField` at the top of the behavior detail. Local state (`editingName`) mirrors `behavior.name`. Commit semantics:

```swift
.onSubmit { commitName(immediate: true) }
.onChange(of: editingName) { _, _ in scheduleNameCommit() }
```

Debounced ~500ms after the last keystroke. `.onDisappear` flushes any pending commit so a swipe-back doesn't drop the latest edit. Empty trimmed name → inline error, no commit. Once non-empty again, the next change triggers a commit.

A `lastCommittedName` `@State` prevents redundant network calls when the value hasn't actually changed across debounced ticks.

### 4. Step row: tap-to-edit

Wrapped each step row in a `Button { itemToEdit = item }` so tapping the row opens the step form. The swipe-trailing **Edit** action was removed (tap replaces it); only **Delete** remains there.

### 5. `TrainerPlanItemFormView` — auto-save in edit mode

The form gained two optional callbacks (callers pick the right one):

```swift
struct TrainerPlanItemFormView: View {
    let mode: ItemFormMode
    var onSave: ((ItemFormResult) -> Void)? = nil    // create — one-shot on Save tap
    var onCommit: ((ItemFormResult) -> Void)? = nil  // edit — auto-fires per commit
    ...
}
```

In edit mode:
- Toolbar Save button is **removed**. Leading toolbar item becomes "Done" (was "Cancel"). Tapping Done flushes pending commit and dismisses.
- `TextField`s call `scheduleDebouncedCommit()` on `.onChange`; pickers call `commitIfEditing(immediate: true)` on `.onChange`.
- `.onDisappear` also flushes — covers swipe-down-to-dismiss.
- An empty trimmed title surfaces a `.font(.caption).foregroundStyle(.red)` error under the field but does **not** commit and does **not** auto-revert. The user is mid-edit.

In create (add) mode: behavior is unchanged. Save button validates, calls `onSave`, dismisses.

`commitTask: Task<Void, Never>?` and `hasPopulated: Bool` guard the lifecycle:
- `hasPopulated` blocks commits during initial `populateIfEditing()` — otherwise the first `.onAppear` would fire spurious commits.
- `commitTask?.cancel()` keeps debounced edits coalesced.

## Android implementation outline

Translate the iOS pattern straight across:

```kotlin
@Composable
fun PlanItemFormScreen(
    editing: TrainingPlanItem?,
    onSave: (ItemFormResult) -> Unit = {},
    onCommit: (ItemFormResult) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val isEditing = editing != null
    var title by remember { mutableStateOf(editing?.title ?: "") }
    // … same for pickers/custom values
    var hasPopulated by remember { mutableStateOf(false) }

    val titleFlow = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }
    LaunchedEffect(Unit) {
        titleFlow.debounce(500).collect { commitIfEditing() }
    }

    LaunchedEffect(title) {
        if (hasPopulated) titleFlow.emit(title)
    }
    LaunchedEffect(distance, duration, distraction) {
        if (hasPopulated) commitIfEditing()   // immediate for pickers
    }

    DisposableEffect(Unit) {
        hasPopulated = true
        onDispose { if (isEditing) commitIfEditing() }   // flush on dispose
    }
    ...
}
```

The Compose-side flush on dispose is the analogue of iOS `.onDisappear`. Use a `SnackbarHost` to add the "Updated • UNDO" snackbar that the Android card called out (the iOS card flagged the same thing as a "nice-to-have but Apple doesn't have native snackbars" stretch; on Android it's free).

## Manual test plan

1. **Trainer**: open a plan → no toolbar Add Behavior → scroll to the bottom of Behaviors → see inline `+ Add Behavior` → tap → form opens → create works as before.
2. Open a behavior → no toolbar Add Step / Rename → name is editable inline at top → type new name → wait ~500ms → confirm it persists (refresh or back-out and reopen).
3. Empty the behavior name → inline error appears, save does not fire. Re-type → commits resume.
4. Tap a step row → form opens in edit mode → toolbar shows **Done** (no Save) → change a picker → confirm change persists immediately (back out and reopen, or check elsewhere). Type into title → wait ~500ms → confirm.
5. Empty the step title → inline error appears, no save. Re-type → resumes.
6. Tap **Done** in edit mode → flushes any pending commit and closes the form.
7. Swipe down to dismiss the edit form → also flushes.
8. **Guardian's own plan**: same flow works via `OwnedPlanDetailView` wrapper.
9. **Guardian's trainer-assigned plan**: read-only behavior preserved — no edit affordance.

## Notes on edge cases iOS handled

- **Initial population shouldn't trigger commit**: `hasPopulated` flag delays commit firing until after `populateIfEditing()` has run.
- **Debounce cancellation**: every keystroke cancels the previous `Task` so only the last typing pause triggers a save.
- **Stale `behavior`/`item` in closures**: the iOS code captures `let item` per-row closure but applies fields from the latest `result`. `updateItem(_:)` targets by `id`, so stale fields beyond what the form mutates are not a concern.
- **Picker `.onChange` immediate commit**: text-related `.onChange`s debounce; picker `.onChange`s call `commitIfEditing(immediate: true)`.

## Known iOS-specific notes

- iOS doesn't have a native Material-style undo snackbar. The iOS card flagged the snackbar as a "stretch" and we shipped without. Android should include it — `SnackbarHostState.showSnackbar(...)` is built into Material 3.
