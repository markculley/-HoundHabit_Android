# AND — Trainer plans list: assignment indicator on each row

Notes from the iOS implementation (shipped same session). Pure UI/data-wiring change — no backend, no schema change.

## Cards

- **Android**: [Trainer plans list: show assignment indicator on each row](https://www.notion.so/35fd8a41762281f4b76ad838503fdf29) — Backlog
- **iOS**: [same title](https://www.notion.so/35fd8a4176228159af07f75db57c6832) — shipped

## Problem

On the trainer's Plans tab there was no signal of which plans are assigned to a guardian — the trainer had to open each plan to find out.

## What iOS did

Three files touched:

| iOS file | Android equivalent |
|---|---|
| `HoundHabit/Core/Services/TrainingPlanService.swift` | the plan service / repository |
| `HoundHabit/Trainer/Plans/TrainerPlanViewModel.swift` | `TrainerPlanViewModel` (or equivalent) |
| `HoundHabit/Trainer/Plans/TrainerPlanListView.swift` | `feature/trainer/plans/PlanListScreen.kt` |

### 1. Bulk-fetch service method

The existing per-plan `fetchAssignments(planId:)` would be an N+1 query if called once per row. iOS added a single bulk fetch scoped by `trainer_id`:

```swift
func fetchAllAssignments() async throws -> [PlanAssignment] {
    guard let trainerId = supabase.auth.currentUser?.id else { return [] }
    return try await supabase
        .from("plan_assignments")
        .select()
        .eq("trainer_id", value: trainerId)
        .execute()
        .value
}
```

`plan_assignments` carries `trainer_id` directly (denormalized to avoid a recursive RLS subquery back to `training_plans`), so this is a flat single-table query. RLS already restricts to the trainer's own rows.

supabase-kt equivalent:

```kotlin
suspend fun fetchAllAssignments(): List<PlanAssignment> {
    val trainerId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
    return supabase.from("plan_assignments")
        .select { filter { eq("trainer_id", trainerId) } }
        .decodeList()
}
```

### 2. View model — load and key by plan id

`TrainerPlanViewModel` already had `assignments: [UUID: [PlanAssignment]]` keyed by plan id, but it was only populated lazily by `loadAssignments(for:)` when a plan detail view appeared. iOS added a bulk loader that fills the whole dict at once:

```swift
func loadAllAssignments() async {
    let all = (try? await service.fetchAllAssignments()) ?? []
    assignments = Dictionary(grouping: all, by: { $0.planId })
}
```

On Android, expose the same shape in the Plans list state — `Map<UUID, List<PlanAssignment>>` — populated from the bulk fetch.

### 3. List screen — load alongside plans, render the badge

`TrainerPlanListView.task` now calls both loaders in sequence:

```swift
.task {
    await viewModel.loadPlans()
    await viewModel.loadAllAssignments()
}
```

The row reads the count and renders an icon + number when non-zero:

```swift
private struct PlanRow: View {
    let plan: TrainingPlan
    let assignmentCount: Int

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(plan.title).font(.headline)
                if let description = plan.description, !description.isEmpty {
                    Text(description).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            if assignmentCount > 0 {
                HStack(spacing: 4) {
                    Image(systemName: "person.fill")
                    Text("\(assignmentCount)")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                .accessibilityLabel("\(assignmentCount) assignment\(assignmentCount == 1 ? "" : "s")")
            }
        }
        .padding(.vertical, 2)
    }
}
```

Call site passes `assignmentCount: viewModel.assignments[plan.id]?.count ?? 0`.

Compose equivalent — trailing slot in the row:

```kotlin
@Composable
fun PlanRow(plan: TrainingPlan, assignmentCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(plan.title, style = MaterialTheme.typography.titleMedium)
            plan.description?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (assignmentCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics {
                    contentDescription = "$assignmentCount assignment" +
                        if (assignmentCount == 1) "" else "s"
                }
            ) {
                Icon(Icons.Filled.Person, contentDescription = null,
                     modifier = Modifier.size(16.dp),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$assignmentCount", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

## Notes / edge cases iOS handled

- **No N+1**: one bulk query for all assignments, not one per plan.
- **Live updates**: the `assignments` dict is already mutated in-memory when an assignment is created/deleted (existing `assignPlan` / `deleteAssignment` paths), so the badge stays correct without a re-fetch.
- **Unassigned rows**: render nothing — no zero badge, no empty placeholder.
- **Canonical file gotcha (iOS-only)**: the iOS repo has a stale duplicate `Trainer/TrainerPlanListView.swift` not in the build; the real one is `Trainer/Plans/TrainerPlanListView.swift`. No Android analogue — just noting it so anyone cross-referencing the iOS tree doesn't edit the wrong file.

## Manual test plan

1. Trainer with a mix of assigned and unassigned plans → Plans tab → assigned plans show `person` icon + count, unassigned show nothing.
2. Assign a plan to a guardian → back to Plans tab → that plan now shows the badge with the right count.
3. Remove an assignment → count decrements; at zero the badge disappears.
4. Plan assigned to multiple guardians → count reflects the total.
5. VoiceOver/TalkBack → the badge reads "N assignments".
