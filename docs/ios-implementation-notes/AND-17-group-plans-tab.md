# AND-17 — Group Plans tab into "My Plans" and "From My Trainer"

Notes from the parallel iOS implementation (card **IOS-14**). Pure UI change — no backend.

## Cards

- **Android**: [AND-17](https://www.notion.so/35ed8a4176228120a7c2f97340245608)
- **iOS**: [IOS-14](https://www.notion.so/35ed8a4176228158be61ec6aae8ed9f5) — shipped first

## What iOS did

Single file: [`HoundHabit/Guardian/Plans/GuardianPlanListView.swift`](https://github.com). Three discrete changes:

### 1. Split the list into two sections

Added two computed properties on the view:

```swift
private var ownPlans: [AssignedPlan] {
    viewModel.assignedPlans.filter { viewModel.isOwnPlan($0) }
}

private var trainerPlans: [AssignedPlan] {
    viewModel.assignedPlans.filter { !viewModel.isOwnPlan($0) }
}
```

The body went from a single flat `ForEach` to two conditional `Section`s:

```swift
List {
    if !ownPlans.isEmpty {
        Section("My Plans") {
            ForEach(ownPlans) { … }   // navigates to OwnedPlanDetailView
        }
    }
    if !trainerPlans.isEmpty {
        Section("From My Trainer") {
            ForEach(trainerPlans) { … }   // navigates to GuardianPlanDetailView
        }
    }
}
```

Empty section ⇒ hidden. Empty *both* sections ⇒ existing `ContentUnavailableView` still shown via the `.overlay { … }` modifier.

### 2. Swipe-to-delete only in "My Plans"

The trainer-assigned section has no swipe action attached. Easy in SwiftUI: just don't add `.swipeActions` to that section's rows. The Android equivalent is "long-press-to-delete only in the My Plans section."

### 3. Show the pet on each row — avatar + name

Added a lookup helper that returns the full `Pet?` (so the row can read both `name` and `photoUrl`):

```swift
private func pet(for assignedPlan: AssignedPlan) -> Pet? {
    guard let petId = assignedPlan.assignment.petId else { return nil }
    return petViewModel.pets.first(where: { $0.id == petId })
}
```

`petViewModel` was already on the view, so no extra fetching. Pass the result into `AssignedPlanRow` as an optional, then:

- **Avatar (40pt circle)** on the **leading edge** of the row, using the existing `PetAvatarView(url: pet?.photoUrl, size: 40)` component. It already falls back to a paw placeholder when `url` is nil — handles the no-pet case gracefully and keeps row alignment uniform.
- **Caption** merges the pet name into the existing "Assigned <date>" line:

```swift
private var captionLine: String {
    let date = assignedPlan.assignment.assignedAt.formatted(date: .abbreviated, time: .omitted)
    if let name = pet?.name, !name.isEmpty {
        return "Assigned \(date) • \(name)"
    }
    return "Assigned \(date)"
}
```

Caption is `.font(.caption2).foregroundStyle(.tertiary)` — subtle, doesn't compete with the title. When the assignment has no `petId` (guardian picked "None") or the pet was deleted, the line reads "Assigned <date>" alone and the avatar shows the paw placeholder.

### Row layout

```
[40pt circle avatar] [title + description + caption stacked] [Spacer] [PlanProgressBadge]
```

Outer `HStack(alignment: .center, spacing: 12)`.

## Android equivalent

| iOS | Android |
|---|---|
| `GuardianPlanListView.swift` | `feature/guardian/plans/GuardianPlanListScreen.kt` |
| `Section("My Plans") { … }` + `Section("From My Trainer") { … }` | Two `item { SubHeader("…") }` blocks inside the `LazyColumn`, each followed by `items(planList)` — mirror the existing `SubHeader` pattern from `PetDetailScreen.kt:194, :201` |
| `.swipeActions(edge: .trailing) { delete }` | The existing `combinedClickable(onLongClick = onLongPress)` on the row; only wire `onLongPress` for own plans, no-op for trainer plans |
| `pet(for:)` view helper (returns `Pet?`) + pass to row | Same lookup against `petsState.pets`, plumbed through to the row composable |
| `PetAvatarView(url: pet?.photoUrl, size: 40)` on the leading edge | Equivalent Material 3 avatar composable on the leading edge (Coil-loaded `AsyncImage` with a paw icon fallback). Size ~40dp |

### Don't forget

- Drop the inline **"Your plan"** badge currently rendered in each own-plan row (`GuardianPlanListScreen.kt:200-211`). The section header makes it redundant.
- Keep the existing global `EmptyState` for the both-empty case (`state.assignedPlans.isEmpty`).
- Keep the create-plan FAB + bottom sheet flow exactly as is.

## Manual test plan

1. Sign in as a guardian who has **only own plans** → see only the **My Plans** section.
2. Sign in as a guardian who has **only trainer-assigned plans** → see only the **From My Trainer** section.
3. Mixed → both sections visible, each plan in the right one.
4. Long-press a plan in **My Plans** → delete confirmation appears, accepting removes it.
5. Long-press a plan in **From My Trainer** → nothing happens (no long-press handler).
6. Tapping rows: own → owned-plan detail; trainer-assigned → assigned-plan detail.
7. Each row's caption reads `Assigned <date> • <pet name>` when a pet is linked, or just `Assigned <date>` when none is linked or the pet is deleted.
8. Each row shows a **circular pet avatar** on the leading edge. With a pet linked, it loads the pet's photo (or a paw placeholder if there's no photo). With no pet, it shows the paw placeholder.
8. With zero plans of any kind → existing empty state ("No Plans Yet…") shows.
9. Tap **+** to create a new plan → new plan appears in My Plans (after the create flow finishes self-assigning).

## Known non-issue

The Pet Detail page already does the same grouping with the same labels (verified on iOS). Keep the same Material 3 `SubHeader` style your Pet Detail uses — terminology and visual treatment should match.
