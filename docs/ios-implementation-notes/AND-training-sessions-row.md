# AND — Training Sessions row redesign + plan-only logging

Notes from the iOS implementation (card **IOS-31**). Two intertwined changes: (1) redesign the Training Sessions row on the Pet detail screen, and (2) remove standalone session logging so every session is plan-linked.

## Cards

- **Android**: [Training Sessions in PetDetailScreen](https://www.notion.so/360d8a41762281b9b0c6fe23b4d311e7) — Backlog
- **iOS**: [IOS-31](https://www.notion.so/360d8a41762280379f9deb0992fe26e4) — Done

## Shared backend — already applied

The 7 pre-existing standalone `training_records` (test data, `plan_item_id IS NULL`) were deleted from the shared Supabase project. **Android needs no DB work.**

`training_records.plan_item_id` stays **nullable** in the DB on purpose — its FK to `training_plan_items` is `ON DELETE SET NULL`, so a NOT NULL constraint would break plan-item deletion. Enforcement that "every session is plan-linked" is UI-level on both platforms.

## Part 1 — Row redesign

Each row in the Pet detail Training Sessions list shows, in order of importance:

1. **Behavior** — headline
2. **Step Name** — subheadline, secondary
3. **DateTime** — caption, tertiary
4. **Notes** — caption, secondary, 2-line limit, only when non-empty
5. **Result** — `score`/5, right-aligned, monospaced digits, colored by status (`TrainingStatus.from(score:)`)

### Resolving Behavior + Step names

A `TrainingRecord` only carries `planItemId` — not the step title or behavior name. iOS resolves them in the view model with **two bulk queries** (no N+1):

```swift
// TrainingPlanService — new bulk fetches
func fetchItems(ids: [UUID]) async throws -> [TrainingPlanItem]   // training_plan_items WHERE id IN (...)
func fetchBehaviors(ids: [UUID]) async throws -> [Behavior]       // behaviors WHERE id IN (...)

// TrainingRecordViewModel.loadRecords — after fetching records:
//   itemIds   = records.compactMap(\.planItemId) |> Set
//   items     = fetchItems(ids: itemIds)
//   behaviorIds = items.compactMap(\.behaviorId) |> Set
//   behaviors = fetchBehaviors(ids: behaviorIds)
// build two [UUID: String] maps keyed by planItemId:
//   stepTitles[planItemId]    = item.title
//   behaviorNames[planItemId] = behaviorName(item.behaviorId)
```

The view passes `behaviorNames[record.planItemId]` and `stepTitles[record.planItemId]` into the row.

For Android: add the two `in(...)` queries to the plan repository; in the `TrainingRecordViewModel` (or whatever loads the Pet detail sessions), after loading records, run the two bulk fetches and expose `Map<UUID, String>` for step titles and behavior names keyed by `planItemId`. Supabase-kt: `.select { filter { isIn("id", ids) } }`.

### The row is PetDetail-only

iOS made a new `PetSessionRow` private to the Pet detail screen rather than changing the shared `TrainingRecordRow` — the trainer's `GuardianDetailView` ("Shared Sessions") keeps its existing row layout untouched. On Android, do the same: a new row composable for the Pet detail screen; leave the trainer's guardian-detail session row as-is.

## Part 2 — Remove standalone logging

iOS's standalone-logging path was entirely in the Pet detail screen: a `+` button in the Training Sessions header opened the session form with no plan item. Removed:

- The `+` button in the Training Sessions header (header is now a plain bold label).
- The standalone session-form sheet + its presentation state.
- Empty-state copy changed: ~~"No sessions yet. Tap + to log one."~~ → **"No training sessions yet. Practice a plan step to log one."**

Kept:

- The session form composable itself — still used by the plan practice flow and for editing existing records.
- The plan practice flow on the plan detail screen, which logs sessions with `plan_item_id` set. This is now the **only** way a guardian logs a session.

### Refresh after logging in the plan sheet

Since logging now happens inside the plan detail (presented from Pet detail), iOS added an `onDismiss` on that presentation to reload the Pet detail's session list. On Android, make sure returning from the plan detail screen re-fetches the Pet detail sessions (e.g. reload in a `LaunchedEffect` keyed on resume, or a result callback).

## File map

| iOS file | What changed | Android equivalent |
|---|---|---|
| `Core/Services/TrainingPlanService.swift` | Added `fetchItems(ids:)` and `fetchBehaviors(ids:)` bulk fetches | plan repository |
| `Guardian/TrainingRecords/TrainingRecordViewModel.swift` | Added `stepTitles` / `behaviorNames` maps + `resolvePlanContext(for:)` called from `loadRecords` | the Pet detail sessions view model |
| `Guardian/Pets/PetDetailView.swift` | Removed standalone-log state + sheet + `+` button; new `PetSessionRow` private view; `onDismiss` on the plan sheet reloads records | `feature/guardian/pets/PetDetailScreen.kt` |

## Manual test plan

1. Pet detail → Training Sessions header has **no `+` button** — just the bold label.
2. Each session row shows Behavior (bold), Step Name (secondary), DateTime, Notes (if any), and `N/5` on the right colored by status.
3. Pet with no sessions → *"No training sessions yet. Practice a plan step to log one."*
4. Open a plan from the Plans section → practice a step → log it → back out → the session appears in the Training Sessions list with the correct Behavior/Step.
5. Tap a row → pushes the session detail. Swipe-share / swipe-delete still work.
6. There is no path anywhere in the guardian UI to log a session that isn't tied to a plan step.
7. Trainer's guardian-detail "Shared Sessions" list is visually unchanged.

## Notes on edge cases iOS handled

- **Bulk fetch, not N+1**: resolve all step/behavior names in two queries, not one per row.
- **Defensive fallback**: the row's title falls back `behaviorName ?? stepTitle ?? "Training session"` — shouldn't be hit now that every session is plan-linked, but cheap insurance.
- **`name resolution is non-fatal`**: if the bulk fetch fails, rows still render (just without the resolved labels) — don't block the list on it.
