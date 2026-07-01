# AND-26 — Copy Plan from plan detail overflow menu

Notes from the parallel iOS implementation (card **IOS-22**), captured so the Android port doesn't have to re-derive decisions.

## Cards

- **Android**: [AND-26](https://www.notion.so/35fd8a417622812bae0bec388251f92b) — `Copy / duplicate a plan from the plan detail overflow menu`
- **iOS**: [IOS-22](https://www.notion.so/35fd8a4176228160964fd722ee2b8546) — same name; shipped first

## Backend status — DEPLOYED, no migration needed

Migration `add_copy_plan_function` is **live in production Supabase** (project `ddrkziwwpmqaophocnhb`). The Android client should call the RPC directly:

```kotlin
@Serializable
private data class CopyPlanParams(val source_plan_id: String)

val newPlanId: String = supabase.postgrest.rpc(
    "copy_plan",
    CopyPlanParams(source_plan_id = planId.toString())
).decodeAs<String>()  // RPC returns a uuid; PostgREST encodes it as a JSON string

val newPlan: TrainingPlan = supabase
    .from("training_plans")
    .select { filter { eq("id", newPlanId) } }
    .decodeSingle()
```

The function signature is `public.copy_plan(source_plan_id uuid) returns uuid`. It is `security definer`, performs the ownership check internally, and grants execute to `authenticated`. SQL source is checked in at `hound_habit/scripts/sql/copy_plan.sql` in the iOS repo.

### Behavior

- Inserts a new `training_plans` row owned by `auth.uid()` with title = `"<source title> (Copy)"`, description copied.
- For each behavior under the source plan: inserts a new behavior row, then copies all `training_plan_items` bound to it, remapping `behavior_id` to the new row.
- Also copies any items with `behavior_id IS NULL` (legacy / pre-Phase-12 plans).
- Does **not** copy `plan_assignments` or `training_records`.

### Error codes

- `42501` (= HTTP 403): `not authenticated` or `not authorized to copy this plan`.

### Why the loop, not a CTE join on sort_order

The original card draft joined old→new behaviors on `sort_order`, but **sort_order is not unique within a plan**. The deployed version loops each source behavior and captures the new id via `RETURNING`, giving a reliable mapping.

## UX decisions that went beyond the spec

These came up during iOS implementation. Mirror them on Android.

### 1. Cancel deletes the copy

The original card said "Sheet dismiss / save → push to new detail." But that left an orphan copy when the user cancelled. PRD line 28 ("no trainer required") permits unassigned plans to exist, but a user explicitly hitting Cancel after Copy expects the copy to be undone — not preserved unassigned.

**Pattern used on iOS**: a `confirmed` flag in parent state, set inside the sheet's `onComplete` callback. The parent's `onDismiss` reads the flag — if true, push to the new plan's detail; if false, delete the copy via the RPC.

### 2. "None" is a real picker option

Instead of disabling the assign button until a guardian is picked (and using button-label tricks), expose **None** as a first-class row in the guardian picker. The picker section becomes `Guardian (Optional)` (parallel to the existing `Pet (Optional)` pattern). Save is always enabled.

This applies only in the copy flow. The regular Assign flow (opened from the plan detail) keeps requiring a real guardian.

### 3. Guardian's copy flow uses a dedicated sheet

The trainer's `AssignPlanSheet` queries linked guardians via `inviteService.fetchLinkedGuardians()` — that returns empty for a guardian (they don't have guardians under them). So:

- Trainer copy → `AssignPlanSheet` with `allowNone = true`
- Guardian copy → **new** `CopyPlanPetPickerSheet` that just loads the guardian's pets, with `None` as a picker option, Cancel deletes the copy, Save calls `selfAssignPlan(planId, petId)`

### 4. Side fix — description label

While I was in the plan-detail header, I also fixed an existing UX issue: the description was rendering as a free-floating `Text` with no label. Now it uses `LabeledContent("Description", value: description)`. Check whether the Android plan detail does the same and fix if so.

### 5. iOS 26 toolbar quirk (Android probably immune)

On iOS 26, having both a primary text-only ToolbarItem AND a secondary Menu produced a weird pill rendering where the primary action wasn't tappable. I worked around it by **moving "Add Behavior" into the same overflow menu** alongside Edit Plan + Copy Plan. Android won't have this iOS 26-specific rendering issue, but the consolidated single-menu approach is consistent with the future state described by the "Inline-add + auto-save" Backlog card anyway.

## iOS file changes — Android equivalents

| iOS file | Change | Android equivalent |
|---|---|---|
| `HoundHabit/Core/Services/TrainingPlanService.swift` | Added `copyPlan(planId:) async throws -> TrainingPlan` that calls the RPC and fetches the new plan | `core/services/TrainingPlanService.kt` |
| `HoundHabit/Trainer/Plans/TrainerPlanViewModel.swift` | Added `copyPlan(_:) async -> TrainingPlan?` that wraps the service call and inserts the new plan into `plans` | `feature/trainer/plans/PlanViewModel.kt` |
| `HoundHabit/Trainer/Plans/TrainerPlanDetailView.swift` | Consolidated toolbar into a single Menu (`Add Behavior` + `Edit Plan` + `Copy Plan`); added state (`isCopying`, `pendingCopyPushTarget`, `copyConfirmed`, etc.); wired post-copy sheets + `navigationDestination(item:)` for the push; description now `LabeledContent` | `feature/trainer/plans/PlanDetailScreen.kt` |
| `HoundHabit/Trainer/Plans/AssignPlanSheet.swift` | Added `allowNone: Bool` param; picker now shows `None` instead of `Select…` when true; section title becomes `Guardian (Optional)`; renamed `onAssign` → `onComplete` | `feature/trainer/plans/AssignPlanSheet.kt` |
| `HoundHabit/Guardian/Plans/CopyPlanPetPickerSheet.swift` | **NEW** sheet for guardian's copy flow | new `feature/guardian/plans/CopyPlanPetPickerSheet.kt` (or wherever the guardian-side plan code lives) |

## Manual test plan

1. **Trainer copy + assign**: Trainer plan → `…` menu → Copy Plan → pick a guardian → Save → land on new copy's detail; copy appears in Plans list; assignment exists.
2. **Trainer copy + None**: same but pick `None` in the Guardian picker → Save → land on new copy's detail; copy exists unassigned.
3. **Trainer copy + Cancel**: same but tap Cancel → no navigation, no copy in Plans list (verify via plans refresh).
4. **Guardian copy + pet**: Guardian's own plan → `…` → Copy Plan → pick a pet → Save → land on copy detail; self-assignment exists.
5. **Guardian copy + None**: same but pick `None` for pet → Save → land on copy detail; copy appears in their assigned-plans list as unlinked.
6. **Guardian copy + Cancel**: same but tap Cancel → no navigation, no copy in plans list.
7. **Hidden for non-owned plans**: as a guardian, open a plan **from your trainer** (`GuardianPlanDetailScreen`) — Copy Plan should not be available (different screen entirely on iOS; verify your Android routing matches).
8. **Recursive copy**: copy a copy. Works.
9. **Empty plan copy**: copying a plan with zero behaviors works — the copy has zero behaviors too. Assignment from `AssignPlanSheet` is technically still possible; matches iOS current behavior. Acceptable per PRD line 28.

## Known follow-ups (not blocking this card)

- After a guardian copies + self-assigns, the **Plans tab list won't auto-refresh**. They need to pull-to-refresh to see the new plan there. The new detail loads fresh; only the back-navigation case is affected. Likely worth a separate small card if it bothers anyone — solved by a shared observable or a callback up the wrapper chain.
- The `description` row in the plan header now uses `LabeledContent`, but **the description on training-record detail and other places may still need similar treatment** — audit if you care about visual consistency.
