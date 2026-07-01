# AND — Behavior type: replace free-text name with a fixed picklist

Notes from the iOS implementation. A behavior's name was free text; it's now a typed enum (`BehaviorType`) picked from a fixed list of 12 standard behaviors.

## Cards

- **Android**: [Behavior type: replace free-text name with a fixed picklist](https://www.notion.so/360d8a41762281f987edfa35b81602b0) — Backlog
- **iOS**: [Behavior type: replace free-text name with a fixed picklist](https://www.notion.so/360d8a4176228161a770f801fa136325) — Done

## Resolved open questions

- **Escape hatch?** No. Strict fixed list — no "Custom/Other" option.
- **Duplicate behaviors in one plan?** Allowed. The picker always shows all 12; a plan may hold the same type twice. No dedup, no filtering.
- **DB constraint?** Yes — a `CHECK` constraint was added.

## Shared backend — already applied

Android needs **no DB work**. As part of the iOS implementation:

- Deleted the one non-conforming prod row: `"Walk 1"` (0 steps — a leftover stub).
- Added a `CHECK` constraint:

```sql
ALTER TABLE behaviors
  ADD CONSTRAINT behaviors_name_valid_type
  CHECK (name IN (
    'Sit', 'Down', 'Leave It', 'Drop It', 'Stand', 'Wait/Stay',
    'Walk', 'Touch', 'Go to Mat', 'Recall', 'Off', 'Attention'
  ));
```

The `behaviors.name` column was **not renamed** — it still holds the value, just constrained now. The stored values are the human-readable labels (`"Sit"`, `"Wait/Stay"`, …), so a not-yet-updated client still displays them fine.

## The enum

```swift
enum BehaviorType: String, Codable, CaseIterable, Hashable {
    case sit = "Sit", down = "Down", leaveIt = "Leave It", dropIt = "Drop It",
         stand = "Stand", waitStay = "Wait/Stay", walk = "Walk", touch = "Touch",
         goToMat = "Go to Mat", recall = "Recall", off = "Off", attention = "Attention"
    var label: String { rawValue }
}
```

**Key decision:** the raw values are the **display labels**, not snake_case like the Three D's enums (`Distance.armsLength = "arms_length"`). Rationale: `behaviors.name` is an existing shared column with human-readable data; using labels as raw values means (a) the migration was a 1-row cleanup instead of converting every row, and (b) an un-updated client keeps rendering correct strings. Kotlin: a string-backed enum with the same 12 raw values.

## File-by-file map

| iOS file | Change | Android equivalent |
|---|---|---|
| `Core/Models/Behavior.swift` | Added `BehaviorType` enum. `Behavior.name: String` → `Behavior.type: BehaviorType`; `CodingKeys` maps `type` → the `"name"` column. | the `Behavior` model / data class |
| `Core/Services/TrainingPlanService.swift` | `BehaviorInsert`/`BehaviorUpdate` DTOs: `name: String` → `type: BehaviorType`. `createBehavior(planId:name:)` → `createBehavior(planId:type:)`. `reorderBehaviors` reads `.type`. `fetchBehaviorName` returns `behavior.type.label`. **Removed `updateBehavior`** — it was only used by the now-deleted inline rename. | plan repository |
| `Trainer/Plans/TrainerPlanViewModel.swift` | `addBehavior(to:name:)` → `addBehavior(to:type:)`. **Removed `updateBehavior`.** | `TrainerPlanViewModel` |
| `Trainer/Plans/TrainerBehaviorFormView.swift` | Rewritten: was a free-text `TextField` + a "Suggestions" section; now a single `Section("Behavior")` list of `BehaviorType.allCases` with a checkmark on the selection. `onSave: (String)` → `onSave: (BehaviorType)`. Dropped the `BehaviorFormMode` enum (the `.edit` case was never used in real code). | the behavior form screen |
| `Trainer/Plans/TrainerBehaviorDetailView.swift` | **Removed the entire `Section("Name")`** — the label, the `TextField`, all the debounced-commit state (`editingName`, `lastCommittedName`, `nameError`, `nameCommitTask`) and the `scheduleNameCommit`/`commitName` funcs (this was the iOS IOS-21 inline rename). Nav title is now `behavior.type.label`. The view is just: nav title + "Steps" header + steps. | the behavior detail screen |
| `Trainer/Plans/TrainerPlanDetailView.swift` | Display sites: `behavior.name` → `behavior.type.label` (the behavior row, the empty-behavior assignment warning). The Add Behavior sheet callback now takes a `BehaviorType`. | trainer plan detail screen |
| `Guardian/Plans/GuardianPlanDetailView.swift` | Behavior-grouped layout subheader + the practice/info sheet `behaviorName` lookups: `behavior.name` → `behavior.type.label`. | guardian plan detail screen |
| `Guardian/TrainingRecords/TrainingRecordViewModel.swift` | `resolvePlanContext` builds `behaviorName` from `behavior.type.label`. | the sessions view model |
| `HoundHabitTests/Models/BehaviorTests.swift` | Updated existing decode tests (`behavior.type == .sit`), added a `BehaviorTypeTests` suite — every stored raw value maps to the right case, `allCases.count == 12`, unknown values don't decode. | instrumented/unit tests |

## Behavior detail screen — note

This removes the Android equivalent of iOS's inline-rename feature. After this change a behavior's type is **set once at creation and not editable** — to change it, delete and re-add the behavior. The detail screen shows only: behavior name (as the screen title), the "Steps" header, the steps list. (This was a deliberate refinement requested after a screenshot review — the inline Name field was redundant with the title.)

## Manual test plan

**Trainer:**
1. Open a plan → Add Behavior → the form is a picker/list of the 12 standard behaviors — no free-text field, no separate Suggestions section. Pick one → Add enables → it appears.
2. Add the **same** behavior type twice → both appear (duplicates allowed).
3. Tap a behavior → detail screen has **no Name section** — just the title, "Steps" header, steps + Add Step.
4. Behavior rows in the plan, and the "[X] has no steps" assignment warning, show the behavior label correctly.

**Guardian:**
5. Plan detail → behavior-grouped layout shows behavior labels as subheaders.
6. Pet detail → Training Sessions rows show the Behavior correctly; sort still works.

**Data:**
7. Attempting to write an invalid `behaviors.name` value fails the DB `CHECK` constraint.

## Notes / gotchas

- **`updateBehavior` is gone** on both the service and the view model — it had exactly one caller (the inline rename) which no longer exists. Don't keep a dead update path on Android either.
- **`BehaviorUpdate` DTO stays** — `reorderBehaviors` still uses it (it re-sends `type` + `sort_order` per row on a drag-reorder).
- **`BehaviorFormMode` was deleted** — the form is always "add". If the Android form has an analogous add/edit mode where edit is unused, drop it too.
- The two remaining prod behaviors after cleanup are `"Down"` ×2 (different plans) and `"Wait/Stay"` ×1 — all valid.
