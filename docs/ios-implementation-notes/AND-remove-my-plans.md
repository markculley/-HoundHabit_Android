# AND — Remove 'My Plans' (guardians can no longer create their own plans)

Notes from the iOS implementation (card **IOS-28**). Product decision: guardians no longer author or own training plans — only trainers create plans and assign them. Self-directed authoring is deferred (see the PRD's v2 Backlog, "Guardian-Created Training Plans").

## Cards

- **Android**: [Remove 'My Plans'](https://www.notion.so/360d8a41762281efae6de012fea679d1) — Backlog
- **iOS**: [IOS-28](https://www.notion.so/360d8a4176228059b7d0eed28006d377) — Done

## Shared backend — already applied

These ran against the shared Supabase project as part of IOS-28. **Android needs no DB work.**

- Dropped the `"Guardian self-assigns own plans"` INSERT policy on `plan_assignments`. The only remaining INSERT policy is `"Trainer assigns own plans to linked guardians"`.
- Deleted 3 orphan guardian-created test plans (cascaded to their behaviors / items / self-assignments; 0 training sessions were linked).

A guardian-created plan was just a `training_plans` row with `trainer_id` = the guardian's own user id, plus a `plan_assignments` row where `trainer_id == guardian_id`. There was never any dedicated schema — the dropped RLS policy was the whole enabling mechanism.

## What iOS removed — file by file

| iOS file | What changed | Android equivalent |
|---|---|---|
| `GuardianPlanListView.swift` | Removed the "My Plans" section, the toolbar `+` create button, the create sheet, the `OwnedPlanDetailView` wrapper struct, and the `navigationDestination(for: TrainingPlan.self)`. Now a flat read-only list of trainer-assigned plans → `GuardianPlanDetailView`. | `feature/guardian/plans/PlanListScreen.kt` |
| `GuardianPlanViewModel.swift` | Deleted `adoptCreatedPlan`, `deleteOwnPlan`, `isOwnPlan`, and the now-unused `currentUserId`. | `GuardianPlanViewModel` |
| `PetDetailView.swift` | Removed own-plan state (`showAddPlanSheet`, `showAssignExistingSheet`, `allOwnPlans`, `selectedOwnPlan`), the create/assign `Menu` in the Plans header, the "My Plans" plan group, and the `AssignExistingPlanSheet` struct. Plans section is now a read-only flat list. | `feature/guardian/pets/PetDetailScreen.kt` |
| `CopyPlanPetPickerSheet.swift` | **Deleted entirely** — it only existed for the guardian copy-own-plan flow. | `CopyPlanPetPickerScreen.kt` / equivalent — delete |
| `TrainerPlanDetailView.swift` | Removed the `showAssignments: Bool` parameter (was only `false` for the guardian-owned wrapper, now gone) and its dead branches; removed the `copyPendingGuardianPetPick` state + sheet; `performCopy()` always uses the trainer `AssignPlanSheet` path now. | `feature/trainer/plans/PlanDetailScreen.kt` |
| `TrainingPlanService.swift` | Deleted `selfAssignPlan(planId:petId:)`. | the plan service / repository |
| `TrainerPlanFormView.swift` | Removed the `pets: [Pet]` parameter and the "Assign to Pet" picker section (guardian-only); simplified `onSave` from `(TrainingPlan, UUID?) -> Void` to `(TrainingPlan) -> Void`. Updated both trainer call sites. | `feature/trainer/plans/PlanFormScreen.kt` |

## Android translation notes

- **The `showAssignments` flag**: iOS had `TrainerPlanDetailView(showAssignments: Bool = true)` — guardians viewing their own plan passed `false` to hide the assignment UI. With guardian-owned plans gone, the flag is always `true`, so it was removed entirely. If the Android `PlanDetailScreen` has an equivalent flag/parameter for "guardian editing own plan", delete it and its branches.
- **Two navigation destination types collapse to one**: iOS previously registered both `navigationDestination(for: TrainingPlan.self)` (own plans) and `navigationDestination(for: AssignedPlan.self)` (assigned). Only the `AssignedPlan` route remains. On Android, if the nav graph has a separate route for guardian-owned plan detail, remove it.
- **Empty state copy**: changed from *"Tap + to create your own plan, or ask your trainer to assign one."* to *"Ask your trainer to assign a training plan."*
- **Pet detail Plans section**: header is now just a label (no `+` / menu). Empty state: *"No training plans for <pet> yet. Your trainer can assign one."*
- **Trainer copy flow is unaffected** — trainers still copy plans via the overflow menu → `AssignPlanSheet`. Only the guardian-side copy variant (`CopyPlanPetPickerSheet`) was removed.
- **`TrainerPlanFormView` is now trainer-only** — no pet picker. If the Android plan form had a pet-picker section gated on a `pets` list, remove it.

## Manual test plan

**As a guardian:**

1. Plans tab → no create/`+` affordance. With plans: a flat list, no "My Plans" / "From My Trainer" sections. With none: empty state reads *"Ask your trainer to assign a training plan."*
2. Plans tab → swipe / long-press a plan row → no delete action.
3. Pet detail → Plans section header has no `+` / menu. Shows trainer-assigned plans for that pet (read-only), or the inline empty message.
4. There is no path anywhere to create, assign, copy, or delete a plan as a guardian.

**As a trainer (regression):**

5. Plans tab `+` still creates a plan; plan detail still has Edit Plan / Copy Plan.
6. Copy Plan still opens the assign sheet; cancel still rolls back the copy.
7. Assigning a plan to a guardian still works.

## Notes on edge cases iOS handled

- **Orphan data**: pre-existing guardian-created plans would become invisible orphans after the UI change (filtered out everywhere). iOS deleted the 3 that existed in prod rather than leaving them. Check the shared DB before/after — there should be 0 rows where `plan_assignments.trainer_id = guardian_id`.
- **`AssignmentInsert` / `PlanError` stay**: on iOS these are still used by the trainer's `assignPlan` — only `selfAssignPlan` was removed, not the shared helpers. Don't over-delete.
- **Project-file housekeeping (iOS-only)**: deleting `CopyPlanPetPickerSheet.swift` required pulling its three entries out of the `.pbxproj`. Android (Gradle directory-scan) just needs the file deleted.
