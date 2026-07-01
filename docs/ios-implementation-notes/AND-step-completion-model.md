# AND — Step completion model: 3-day streak + sequential gating

Notes from the iOS implementation (`IOS-35`). Replaces the old `current_item_id` advancement pointer with per-step completion + sequential gating within a behavior.

## Cards

- **Android**: [Step completion model](https://www.notion.so/360d8a4176228122b7bbc2c77f03feb1) — Backlog
- **iOS**: "Step completion model: 3-day streak of perfect scores" — Done

## The model

### Completion — 3-day streak

A step is **complete** once the guardian has logged a **score of 5 on it on 3 consecutive calendar days**. Rules:

- Calendar days, by `training_records.recorded_at`, in the **device calendar** (`Calendar.current`). Android must use the device calendar too so the two clients agree per-user.
- Multiple score-5 sessions on the same day count as **one** day.
- Non-5 scores don't count; a gap resets the run.
- **Sticky / any historical run**: *any* run of 3 consecutive perfect days in the step's whole history makes it complete, and it stays complete forever after.

iOS algorithm (`GuardianPlanViewModel.stepCompletion(planItemId:)` → `(isComplete, bestStreak)`):

```
perfectDays = distinct startOfDay(recorded_at) for records where
              planItemId matches AND score == 5
sort perfectDays ascending
best = 1, run = 1
for each adjacent pair (prev, curr):
    if curr == prev + 1 day:  run += 1   else  run = 1
    best = max(best, run)
isComplete = best >= 3
```

`bestStreak` (the longest historical run) is what the UI shows as the "N / 3 days" counter while a step is incomplete.

### Locking — sequential within a behavior

A step is **locked** until the previous step in the **same behavior** is complete. `GuardianPlanViewModel.isStepLocked(_:)`:

```
siblings = items in this step's behavior, sorted by sortOrder
index = siblings.indexOf(step)
if index == 0: not locked          // first step of a behavior is always open
locked = !stepCompletion(siblings[index-1]).isComplete
```

Behaviors are **independent** — completing a step in "Touch" never affects "Down". Steps with no behavior (legacy data) are sequenced among themselves.

Both functions are **pure logic** — unit-test them (iOS has `StepCompletionTests` + `StepLockingTests` covering consecutive/gap/same-day/low-score/sticky and first-step/locked/independent-behaviors).

## UI

Each step row has three states:

| State | Visual |
|---|---|
| Complete | Green circle badge with a white checkmark; green play icon; still tappable (re-trainable) |
| In progress (unlocked, not complete) | Grey circle + step number; grey play icon; orange "N / 3 days" caption when N > 0 |
| Locked | Row dimmed (~55% opacity); lock icon instead of play; "Complete the previous step first" caption; **not tappable** (button disabled) |

The old current/completed/locked tri-state, the "current step" highlight, and the read-only locked-step info sheet are all gone.

## Data loading

`GuardianPlanViewModel` gained `records: [TrainingRecord]` (all the guardian's records), loaded on the plan-detail screen's `.task` and refreshed by `TrainingSessionView` after each logged session (so completion + the next step's unlock update immediately). On Android, load the guardian's training records into the plan view model and recompute completion/locking off that list.

## No advancement message

The advancement alert is **gone**. `TrainingSessionView` just logs the record and dismisses.

## ⚠️ Deliberately left running — `current_item_id`

iOS **kept** `advanceCurrentStep` / `current_item_id` writing after a plan-linked session — silently, with no message — *only* so the plan-progress badge (To Do / In Progress / Done) keeps working in the interim. That whole pointer (and the column) is removed in a **separate deferred card**: "Rework plan progress badge for the completion model". Android can either follow the same staging, or do both together. Don't invest in porting the advancement *logic* — it's on death row.

## File map

| iOS file | Change | Android equivalent |
|---|---|---|
| `Guardian/Plans/GuardianPlanViewModel.swift` | Added `records`, `loadRecords()`, `stepCompletion(_:)`, `isStepLocked(_:)`. Kept `advanceCurrentStep` (vestigial). | plan view model |
| `Guardian/Plans/GuardianPlanDetailView.swift` | Removed the current/locked tri-state, `isLocked`/`orderedItems`/`currentItem`, and `StepInfoSheet` (deleted). New `StepRow` with complete / in-progress / locked states. `.task` also loads records. | plan detail screen |
| `Guardian/TrainingRecords/TrainingSessionView.swift` | Removed the advancement alert; on log it calls `loadRecords()` then dismisses. | the training session screen |
| `HoundHabitTests/Logic/TrainingPlanLogicTests.swift` | Added `StepCompletionTests` + `StepLockingTests`. | unit tests |

## Manual test plan

1. Plan detail → first step of each behavior is unlocked; later steps are dimmed + 🔒 until the previous one is complete.
2. Tapping a locked step does nothing.
3. Log a score-5 on a step across 3 consecutive calendar days → it shows the green checkmark badge, and the next step in that behavior unlocks.
4. 1–2 perfect days → step shows "1 / 3 days" / "2 / 3 days"; a gap or a non-5 day resets it.
5. Completing a step in behavior A doesn't unlock anything in behavior B.
6. No advancement message ever appears after logging.
